package com.accessorchestrator.service;

import com.accessorchestrator.domain.Entitlement;
import com.accessorchestrator.domain.MembershipStatus;
import com.accessorchestrator.domain.Project;
import com.accessorchestrator.domain.ProjectMember;
import com.accessorchestrator.domain.User;
import com.accessorchestrator.dto.AccessRequestDto;
import com.accessorchestrator.dto.DtoMapper;
import com.accessorchestrator.dto.RemovalDtos.ProjectMemberDto;
import com.accessorchestrator.dto.RemovalDtos.RemovalPreviewDto;
import com.accessorchestrator.dto.RemovalDtos.RemovalResultDto;
import com.accessorchestrator.dto.RemovalDtos.RevocationItemDto;
import com.accessorchestrator.dto.UserAccessDto;
import com.accessorchestrator.exception.BusinessRuleException;
import com.accessorchestrator.exception.ForbiddenException;
import com.accessorchestrator.repository.EntitlementRepository;
import com.accessorchestrator.repository.ProjectMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Removing a person from a project: ends the membership (kept for audit), withdraws their pending grant
 * requests for it, and asks the IGA to revoke the project's access they hold. The IGA approves and
 * deprovisions; access is marked REVOKED only when it reports completion.
 */
@Service
@Transactional(readOnly = true)
public class ProjectRemovalService {

    private static final Logger log = LoggerFactory.getLogger(ProjectRemovalService.class);
    private static final int MIN_REASON_LENGTH = 3;

    private final UserService userService;
    private final ProjectService projectService;
    private final AccessAuthority authority;
    private final ProjectMemberRepository members;
    private final AccessAnalysisService accessAnalysisService;
    private final AccessRequestService accessRequestService;
    private final EntitlementRepository entitlements;

    public ProjectRemovalService(UserService userService, ProjectService projectService, AccessAuthority authority,
                                 ProjectMemberRepository members, AccessAnalysisService accessAnalysisService,
                                 AccessRequestService accessRequestService, EntitlementRepository entitlements) {
        this.userService = userService;
        this.projectService = projectService;
        this.authority = authority;
        this.members = members;
        this.accessAnalysisService = accessAnalysisService;
        this.accessRequestService = accessRequestService;
        this.entitlements = entitlements;
    }

    /** Active members of a project; admins only. */
    public List<ProjectMemberDto> members(String actorUserId, Long projectId) {
        User actor = userService.findUser(actorUserId);
        Project project = projectService.findProject(projectId);
        if (!authority.canListProjectMembers(actor)) {
            throw new ForbiddenException("Only administrators can list the members of " + project.getProjectName());
        }
        return members.findByProject_IdAndStatusOrderByJoinedDateAsc(project.getId(), MembershipStatus.ACTIVE).stream()
                .map(m -> new ProjectMemberDto(DtoMapper.toDto(m.getUser()), m.getProjectRole(), m.getJoinedDate()))
                .toList();
    }

    public RemovalPreviewDto preview(String actorUserId, String targetUserId, Long projectId) {
        User actor = userService.findUser(actorUserId);
        User target = userService.findUser(targetUserId);
        Project project = projectService.findProject(projectId);
        ProjectMember membership = activeMembership(actor, target, project);

        RevocationCalculator.Plan plan = RevocationCalculator.plan(
                projectService.getProjectAccess(project.getId()),
                accessAnalysisService.getExistingAccess(target.getUserId()));

        List<String> blockers = new ArrayList<>();
        accessRequestService.findProvisioningGrants(target, project).forEach(r -> blockers.add("Request "
                + (r.igaRequestId() != null ? r.igaRequestId() : r.requestId())
                + " is already being provisioned; try again when it completes"));

        List<RevocationItemDto> toRevoke = plan.toRevoke().stream()
                .map(r -> new RevocationItemDto(r.access().entitlement(), r.reason(), r.access().grantedDate(),
                        r.access().source().name()))
                .toList();
        return new RemovalPreviewDto(DtoMapper.toDto(target), DtoMapper.toDto(project), membership.getProjectRole(),
                membership.getJoinedDate(), actor.getId().equals(target.getId()), authority.basis(actor, target),
                toRevoke, plan.keptDefault(),
                accessRequestService.findCancellableGrants(target, project), blockers, blockers.isEmpty());
    }

    /**
     * Runs the removal. All-or-nothing locally: if the IGA cannot be reached the membership stays active and
     * the caller can retry.
     */
    @Transactional
    public RemovalResultDto remove(String actorUserId, String targetUserId, Long projectId, String reason) {
        if (!StringUtils.hasText(reason) || reason.trim().length() < MIN_REASON_LENGTH) {
            throw new BusinessRuleException("A reason is required to remove someone from a project");
        }
        String why = reason.trim();
        RemovalPreviewDto preview = preview(actorUserId, targetUserId, projectId);
        if (!preview.canProceed()) {
            throw new BusinessRuleException(String.join("; ", preview.blockers()));
        }
        User target = userService.findUser(targetUserId);
        Project project = projectService.findProject(projectId);
        String actorId = userService.findUser(actorUserId).getUserId();

        ProjectMember membership = members.findByUser_IdAndProject_IdAndStatus(target.getId(), project.getId(),
                MembershipStatus.ACTIVE).orElseThrow();
        membership.end(actorId, why);

        List<AccessRequestDto> cancelled = accessRequestService.cancelOpenGrants(target, project, actorId,
                "Removed from " + project.getProjectName() + ": " + why);

        AccessRequestDto revocation = null;
        if (!preview.toRevoke().isEmpty()) {
            List<Entitlement> revoke = preview.toRevoke().stream()
                    .map(i -> entitlements.getReferenceById(i.entitlement().id())).toList();
            Map<Long, String> reasons = new LinkedHashMap<>();
            preview.toRevoke().forEach(i -> reasons.put(i.entitlement().id(), i.reason()));
            revocation = accessRequestService.createRevocation(target, project, revoke, reasons, actorId, why);
        }
        log.info("{} removed {} from {} ({}); revocation {}; cancelled {}", actorId, target.getUserId(),
                project.getProjectCode(), why, revocation == null ? "none" : revocation.igaRequestId(),
                cancelled.stream().map(AccessRequestDto::requestId).toList());

        List<UserAccessDto> kept = preview.keptDefault();
        return new RemovalResultDto(preview.employee(), preview.project(), why, actorId, membership.getRemovedAt(),
                revocation, cancelled, kept);
    }

    private ProjectMember activeMembership(User actor, User target, Project project) {
        if (!authority.canRemove(actor, target)) {
            throw new ForbiddenException("You can remove yourself, or people who report to you (up to "
                    + AccessAuthority.MAX_LEVELS + " levels down); " + target.getName() + " (" + target.getUserId()
                    + ") does not report to you");
        }
        return members.findByUser_IdAndProject_IdAndStatus(target.getId(), project.getId(), MembershipStatus.ACTIVE)
                .orElseThrow(() -> new BusinessRuleException(target.getName() + " (" + target.getUserId()
                        + ") is not an active member of " + project.getProjectName()));
    }
}
