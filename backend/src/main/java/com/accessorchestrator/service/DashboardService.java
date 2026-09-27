package com.accessorchestrator.service;

import com.accessorchestrator.domain.MembershipStatus;
import com.accessorchestrator.domain.User;
import com.accessorchestrator.dto.AccessComparisonResponse;
import com.accessorchestrator.dto.AccessRequestDto;
import com.accessorchestrator.dto.DashboardDto;
import com.accessorchestrator.dto.DtoMapper;
import com.accessorchestrator.dto.OpenRequestDto;
import com.accessorchestrator.dto.ProjectMembershipDto;
import com.accessorchestrator.dto.UserAccessDto;
import com.accessorchestrator.repository.ProjectMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-only aggregates for the UI; all numbers come from the same deterministic services as the APIs. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int RECENT_DAYS = 30;
    private static final int RECENT_LIMIT = 5;

    private final UserService userService;
    private final AccessAnalysisService accessAnalysisService;
    private final AccessRequestService accessRequestService;
    private final ProjectMemberRepository projectMemberRepository;

    public DashboardService(UserService userService, AccessAnalysisService accessAnalysisService,
                            AccessRequestService accessRequestService,
                            ProjectMemberRepository projectMemberRepository) {
        this.userService = userService;
        this.accessAnalysisService = accessAnalysisService;
        this.accessRequestService = accessRequestService;
        this.projectMemberRepository = projectMemberRepository;
    }

    public List<ProjectMembershipDto> getMyProjects(String userId) {
        User user = userService.findUser(userId);
        return projectMemberRepository.findByUser_IdAndStatusOrderByJoinedDateDesc(user.getId(), MembershipStatus.ACTIVE).stream()
                .map(m -> new ProjectMembershipDto(DtoMapper.toDto(m.getProject()), m.getProjectRole(),
                        m.getJoinedDate(),
                        accessAnalysisService.compare(user.getUserId(), m.getProject().getId()).summary()))
                .toList();
    }

    public DashboardDto getDashboard(String userId) {
        User user = userService.findUser(userId);
        List<ProjectMembershipDto> projects = getMyProjects(user.getUserId());

        Set<Long> missing = new HashSet<>();
        for (ProjectMembershipDto p : projects) {
            AccessComparisonResponse c = accessAnalysisService.compare(user.getUserId(), p.project().id());
            c.missingAccess().forEach(pa -> missing.add(pa.entitlement().id()));
        }
        Set<Long> inFlight = accessRequestService.findOpenRequests(user.getUserId()).stream()
                .map(OpenRequestDto::entitlementId)
                .collect(Collectors.toSet());
        int missingRequested = (int) missing.stream().filter(inFlight::contains).count();

        List<UserAccessDto> active = accessAnalysisService.getExistingAccess(user.getUserId());
        LocalDate since = LocalDate.now().minusDays(RECENT_DAYS);
        List<UserAccessDto> recent = active.stream()
                .filter(a -> a.grantedDate() != null && !a.grantedDate().isBefore(since))
                .sorted(Comparator.comparing(UserAccessDto::grantedDate).reversed())
                .limit(RECENT_LIMIT)
                .toList();

        List<AccessRequestDto> pending = accessRequestService.listRequestsForUser(user.getUserId()).stream()
                .filter(r -> r.igaRequestId() != null && !r.status().isTerminal())
                .toList();

        return new DashboardDto(DtoMapper.toDto(user), active.size(), pending.size(), projects.size(),
                missing.size(), missingRequested, projects, active, pending, recent);
    }
}
