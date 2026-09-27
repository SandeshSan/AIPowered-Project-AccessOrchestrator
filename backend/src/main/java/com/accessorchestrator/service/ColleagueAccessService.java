package com.accessorchestrator.service;

import com.accessorchestrator.domain.MembershipStatus;
import com.accessorchestrator.domain.Project;
import com.accessorchestrator.domain.RiskLevel;
import com.accessorchestrator.domain.User;
import com.accessorchestrator.dto.ColleagueComparisonDtos.ColleagueComparisonDto;
import com.accessorchestrator.dto.ColleagueComparisonDtos.ComparedEntitlement;
import com.accessorchestrator.dto.DtoMapper;
import com.accessorchestrator.dto.EntitlementDto;
import com.accessorchestrator.dto.OpenRequestDto;
import com.accessorchestrator.dto.ProjectAccessDto;
import com.accessorchestrator.dto.UserAccessDto;
import com.accessorchestrator.exception.BusinessRuleException;
import com.accessorchestrator.repository.ProjectMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * "What access does Asha have that I don't?" Anyone may compare their active access with anyone's. The
 * colleague's high-risk entitlements are restricted (application and risk only) unless the viewer may see that
 * person's access in full: their manager up the reporting line, or an admin. Every comparison is audit-logged.
 * <p>
 * Comparing never grants anything: an item is only marked requestable when it is part of the required access of
 * one of the viewer's own projects, so a request still goes through the normal project flow and IGA approval.
 */
@Service
@Transactional(readOnly = true)
public class ColleagueAccessService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.access-comparison");
    private static final Comparator<EntitlementDto> ORDER =
            Comparator.comparing(EntitlementDto::application).thenComparing(EntitlementDto::entitlementCode);

    private final UserService userService;
    private final AccessAnalysisService accessAnalysisService;
    private final AccessRequestService accessRequestService;
    private final AccessAuthority accessAuthority;
    private final ProjectMemberRepository members;

    public ColleagueAccessService(UserService userService, AccessAnalysisService accessAnalysisService,
                                  AccessRequestService accessRequestService, AccessAuthority accessAuthority,
                                  ProjectMemberRepository members) {
        this.userService = userService;
        this.accessAnalysisService = accessAnalysisService;
        this.accessRequestService = accessRequestService;
        this.accessAuthority = accessAuthority;
        this.members = members;
    }

    public ColleagueComparisonDto compare(String viewerUserId, String colleagueUserId) {
        User viewer = userService.findUser(viewerUserId);
        User colleague = userService.findUser(colleagueUserId);
        if (viewer.getUserId().equalsIgnoreCase(colleague.getUserId())) {
            throw new BusinessRuleException("That is the signed-in user; compare with someone else, or ask for "
                    + "your own access instead");
        }
        boolean fullDetail = accessAuthority.canViewAccess(viewer, colleague);

        Map<Long, EntitlementDto> mine = byId(accessAnalysisService.getExistingAccess(viewer.getUserId()));
        Map<Long, EntitlementDto> theirs = byId(accessAnalysisService.getExistingAccess(colleague.getUserId()));
        Map<Long, Project> requiredForMe = requiredByMyProjects(viewer);
        Map<Long, OpenRequestDto> inProgress = accessRequestService.findOpenRequests(viewer.getUserId()).stream()
                .collect(Collectors.toMap(OpenRequestDto::entitlementId, o -> o, (a, b) -> a));

        List<ComparedEntitlement> colleagueOnly = theirs.values().stream()
                .filter(e -> !mine.containsKey(e.id()))
                .sorted(ORDER)
                .map(e -> colleagueOnly(e, fullDetail, colleague, requiredForMe, inProgress))
                .toList();
        List<ComparedEntitlement> youOnly = mine.values().stream()
                .filter(e -> !theirs.containsKey(e.id()))
                .sorted(ORDER)
                .map(e -> new ComparedEntitlement(e.id(), e.application(), e.entitlementCode(), e.entitlementName(),
                        e.riskLevel(), false, null, null, null))
                .toList();
        int inCommon = (int) mine.keySet().stream().filter(theirs::containsKey).count();

        AUDIT.info("{} compared access with {} (full detail: {}, colleague-only: {}, restricted: {})",
                viewer.getUserId(), colleague.getUserId(), fullDetail, colleagueOnly.size(),
                colleagueOnly.stream().filter(ComparedEntitlement::restricted).count());
        return new ColleagueComparisonDto(DtoMapper.toDto(colleague), fullDetail, colleagueOnly, youOnly, inCommon);
    }

    private static ComparedEntitlement colleagueOnly(EntitlementDto e, boolean fullDetail, User colleague,
                                                     Map<Long, Project> requiredForMe,
                                                     Map<Long, OpenRequestDto> inProgress) {
        if (!fullDetail && e.riskLevel() == RiskLevel.HIGH) {
            return new ComparedEntitlement(null, e.application(), null, null, e.riskLevel(), true, null, null,
                    "Restricted: high-risk access is shown in detail only to " + colleague.getName()
                            + "'s managers and to admins.");
        }
        OpenRequestDto open = inProgress.get(e.id());
        Project project = requiredForMe.get(e.id());
        if (open != null) {
            String shownId = open.igaRequestId() != null ? open.igaRequestId() : open.requestId();
            return item(e, "You already have a request in progress for this (" + shownId + ").");
        }
        if (project != null) {
            return new ComparedEntitlement(e.id(), e.application(), e.entitlementCode(), e.entitlementName(),
                    e.riskLevel(), false, project.getId(), project.getProjectName(),
                    "Part of the required access for your project " + project.getProjectName() + ".");
        }
        return item(e, "Not part of the required access of any of your projects; it would need a "
                + "business reason, so ask your manager.");
    }

    /** A colleague-only entitlement the viewer cannot request through their projects. */
    private static ComparedEntitlement item(EntitlementDto e, String note) {
        return new ComparedEntitlement(e.id(), e.application(), e.entitlementCode(), e.entitlementName(),
                e.riskLevel(), false, null, null, note);
    }

    /** Entitlement id -> the viewer's (most recently joined) active project that requires it. */
    private Map<Long, Project> requiredByMyProjects(User viewer) {
        Map<Long, Project> required = new LinkedHashMap<>();
        members.findByUser_IdAndStatusOrderByJoinedDateDesc(viewer.getId(), MembershipStatus.ACTIVE).forEach(m -> {
            for (ProjectAccessDto pa : accessAnalysisService.getRequiredAccess(m.getProject().getId())) {
                required.putIfAbsent(pa.entitlement().id(), m.getProject());
            }
        });
        return required;
    }

    private static Map<Long, EntitlementDto> byId(List<UserAccessDto> access) {
        return access.stream().map(UserAccessDto::entitlement)
                .collect(Collectors.toMap(EntitlementDto::id, e -> e, (a, b) -> a, LinkedHashMap::new));
    }
}
