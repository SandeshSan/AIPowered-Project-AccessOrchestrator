package com.accessorchestrator.service;

import com.accessorchestrator.domain.AccessStatus;
import com.accessorchestrator.domain.Project;
import com.accessorchestrator.domain.User;
import com.accessorchestrator.dto.AccessAnalysisResponse;
import com.accessorchestrator.dto.AccessComparisonItemDto;
import com.accessorchestrator.dto.AccessComparisonResponse;
import com.accessorchestrator.dto.AccessVerificationDto;
import com.accessorchestrator.dto.DtoMapper;
import com.accessorchestrator.dto.ProjectAccessDto;
import com.accessorchestrator.dto.UserAccessDto;
import com.accessorchestrator.repository.ProjectAccessRepository;
import com.accessorchestrator.repository.UserAccessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Deterministic access gap analysis: <b>missing = required - existing</b>.
 * <p>
 * This is the single source of truth for what a user lacks on a project. The AI layer may call and
 * explain these results but must never compute or alter them.
 */
@Service
@Transactional(readOnly = true)
public class AccessAnalysisService {

    private final UserService userService;
    private final ProjectService projectService;
    private final ProjectAccessRepository projectAccessRepository;
    private final UserAccessRepository userAccessRepository;

    public AccessAnalysisService(UserService userService,
                                 ProjectService projectService,
                                 ProjectAccessRepository projectAccessRepository,
                                 UserAccessRepository userAccessRepository) {
        this.userService = userService;
        this.projectService = projectService;
        this.projectAccessRepository = projectAccessRepository;
        this.userAccessRepository = userAccessRepository;
    }

    /** Mandatory entitlements of the project's standard access profile. */
    public List<ProjectAccessDto> getRequiredAccess(Long projectId) {
        return requiredAccess(projectService.findProject(projectId));
    }

    /** Entitlements the user currently holds (ACTIVE only; revoked/expired do not count). */
    public List<UserAccessDto> getExistingAccess(String userId) {
        return existingAccess(userService.findUser(userId));
    }

    public List<ProjectAccessDto> calculateMissingAccess(String userId, Long projectId) {
        return analyze(userId, projectId).missingAccess();
    }

    public AccessAnalysisResponse analyze(String userId, Long projectId) {
        User user = userService.findUser(userId);
        Project project = projectService.findProject(projectId);

        List<ProjectAccessDto> required = requiredAccess(project);
        List<UserAccessDto> existing = existingAccess(user);
        List<ProjectAccessDto> missing = verifiedMissing(required, existing);

        return new AccessAnalysisResponse(user.getUserId(), user.getName(), project.getId(),
                project.getProjectName(), required, existing, missing);
    }

    /** Per-entitlement GRANTED/MISSING comparison with summary, additional access and verification. */
    public AccessComparisonResponse compare(String userId, Long projectId) {
        User user = userService.findUser(userId);
        Project project = projectService.findProject(projectId);

        List<ProjectAccessDto> required = requiredAccess(project);
        List<UserAccessDto> existing = existingAccess(user);
        List<ProjectAccessDto> missing = AccessGapCalculator.missing(required, existing);
        AccessVerificationDto verification = requireVerified(required, existing, missing);

        List<AccessComparisonItemDto> items = AccessGapCalculator.compareItems(required, existing);
        List<UserAccessDto> additional = AccessGapCalculator.additional(required, existing);

        return new AccessComparisonResponse(user.getUserId(), user.getName(), project.getId(),
                project.getProjectName(), AccessGapCalculator.summarize(items, additional.size()), items, missing,
                additional, verification);
    }

    private static List<ProjectAccessDto> verifiedMissing(List<ProjectAccessDto> required,
                                                          List<UserAccessDto> existing) {
        List<ProjectAccessDto> missing = AccessGapCalculator.missing(required, existing);
        requireVerified(required, existing, missing);
        return missing;
    }

    /** Fail closed: never hand an unverified gap to a caller (or, later, to the AI / IGA). */
    private static AccessVerificationDto requireVerified(List<ProjectAccessDto> required,
                                                         List<UserAccessDto> existing,
                                                         List<ProjectAccessDto> missing) {
        AccessVerificationDto verification = AccessGapCalculator.verify(required, existing, missing);
        if (!verification.verified()) {
            throw new IllegalStateException("Access gap verification failed: " + verification.expression());
        }
        return verification;
    }

    private List<ProjectAccessDto> requiredAccess(Project project) {
        return projectAccessRepository.findByProject_IdAndRequiredTrue(project.getId()).stream()
                .map(DtoMapper::toDto)
                .sorted(AccessOrdering.PROJECT_ACCESS)
                .toList();
    }

    private List<UserAccessDto> existingAccess(User user) {
        return userAccessRepository.findByUser_IdAndStatus(user.getId(), AccessStatus.ACTIVE).stream()
                .map(DtoMapper::toDto)
                .sorted(AccessOrdering.USER_ACCESS)
                .toList();
    }
}
