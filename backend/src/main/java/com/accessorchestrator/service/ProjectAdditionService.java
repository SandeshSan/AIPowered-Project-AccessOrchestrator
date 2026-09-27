package com.accessorchestrator.service;

import com.accessorchestrator.domain.MembershipStatus;
import com.accessorchestrator.domain.Project;
import com.accessorchestrator.domain.ProjectMember;
import com.accessorchestrator.domain.ProjectStatus;
import com.accessorchestrator.domain.User;
import com.accessorchestrator.domain.UserStatus;
import com.accessorchestrator.dto.AccessComparisonResponse;
import com.accessorchestrator.dto.AdditionDtos.AdditionPreviewDto;
import com.accessorchestrator.dto.AdditionDtos.AdditionResultDto;
import com.accessorchestrator.dto.ComparisonStatus;
import com.accessorchestrator.dto.DtoMapper;
import com.accessorchestrator.dto.EntitlementDto;
import com.accessorchestrator.dto.OpenRequestDto;
import com.accessorchestrator.dto.ProjectAccessDto;
import com.accessorchestrator.exception.BusinessRuleException;
import com.accessorchestrator.exception.ForbiddenException;
import com.accessorchestrator.repository.ProjectMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adding a person to a project on a manager's (or admin's) initiative: makes them an active member. No access is
 * requested; the preview only shows what they will need, and they ask for it themselves (e.g. via the assistant).
 */
@Service
@Transactional(readOnly = true)
public class ProjectAdditionService {

    private static final Logger log = LoggerFactory.getLogger(ProjectAdditionService.class);

    private final UserService userService;
    private final ProjectService projectService;
    private final AccessAuthority authority;
    private final ProjectMemberRepository members;
    private final AccessAnalysisService accessAnalysisService;
    private final AccessRequestService accessRequestService;

    public ProjectAdditionService(UserService userService, ProjectService projectService, AccessAuthority authority,
                                  ProjectMemberRepository members, AccessAnalysisService accessAnalysisService,
                                  AccessRequestService accessRequestService) {
        this.userService = userService;
        this.projectService = projectService;
        this.authority = authority;
        this.members = members;
        this.accessAnalysisService = accessAnalysisService;
        this.accessRequestService = accessRequestService;
    }

    public AdditionPreviewDto preview(String actorUserId, String targetUserId, Long projectId, String projectRole) {
        User actor = userService.findUser(actorUserId);
        User target = userService.findUser(targetUserId);
        Project project = projectService.findProject(projectId);
        if (!authority.canAdd(actor, target)) {
            throw new ForbiddenException("You can add people who report to you (up to " + AccessAuthority.MAX_LEVELS
                    + " levels down) to projects; " + target.getName() + " (" + target.getUserId()
                    + ") does not report to you");
        }

        List<String> blockers = new ArrayList<>();
        if (target.getStatus() != UserStatus.ACTIVE) {
            blockers.add(target.getName() + " is not an active employee");
        }
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            blockers.add(project.getProjectName() + " is not an active project");
        }
        if (members.findByUser_IdAndProject_IdAndStatus(target.getId(), project.getId(), MembershipStatus.ACTIVE)
                .isPresent()) {
            blockers.add(target.getName() + " is already a member of " + project.getProjectName());
        }

        AccessComparisonResponse comparison = accessAnalysisService.compare(target.getUserId(), project.getId());
        List<EntitlementDto> have = comparison.items().stream()
                .filter(i -> i.status() == ComparisonStatus.GRANTED).map(i -> i.entitlement()).toList();
        Set<Long> missingIds = comparison.missingAccess().stream().map(pa -> pa.entitlement().id())
                .collect(Collectors.toSet());
        List<OpenRequestDto> inFlight = accessRequestService.findOpenRequests(target.getUserId()).stream()
                .filter(o -> missingIds.contains(o.entitlementId())).toList();
        Set<Long> inFlightIds = inFlight.stream().map(OpenRequestDto::entitlementId).collect(Collectors.toSet());
        List<ProjectAccessDto> missing = comparison.missingAccess().stream()
                .filter(pa -> !inFlightIds.contains(pa.entitlement().id())).toList();

        String role = StringUtils.hasText(projectRole) ? projectRole.trim() : target.getRole();
        return new AdditionPreviewDto(DtoMapper.toDto(target), DtoMapper.toDto(project), role,
                authority.basis(actor, target), have, missing, inFlight, blockers, blockers.isEmpty());
    }

    @Transactional
    public AdditionResultDto add(String actorUserId, String targetUserId, Long projectId, String projectRole) {
        AdditionPreviewDto preview = preview(actorUserId, targetUserId, projectId, projectRole);
        if (!preview.canProceed()) {
            throw new BusinessRuleException(String.join("; ", preview.blockers()));
        }
        User target = userService.findUser(targetUserId);
        Project project = projectService.findProject(projectId);
        String actorId = userService.findUser(actorUserId).getUserId();

        members.findByUser_IdAndProject_Id(target.getId(), project.getId()).ifPresentOrElse(
                ended -> ended.rejoin(preview.projectRole(), LocalDate.now()),
                () -> members.save(new ProjectMember(target, project, preview.projectRole(), LocalDate.now())));

        log.info("{} added {} to {} as {}; {} required entitlement(s) still to be requested", actorId,
                target.getUserId(), project.getProjectCode(), preview.projectRole(), preview.missingAccess().size());
        return new AdditionResultDto(preview.employee(), preview.project(), preview.projectRole(), actorId,
                preview.missingAccess());
    }
}
