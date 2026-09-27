package com.accessorchestrator.dto;

import java.util.List;

/**
 * Full side-by-side comparison of a user's ACTIVE access against a project's required access profile.
 *
 * @param items            one row per required entitlement (GRANTED or MISSING)
 * @param missingAccess    required - existing
 * @param additionalAccess existing - required (held but not needed for this project)
 */
public record AccessComparisonResponse(
        String userId,
        String userName,
        Long projectId,
        String project,
        AccessComparisonSummary summary,
        List<AccessComparisonItemDto> items,
        List<ProjectAccessDto> missingAccess,
        List<UserAccessDto> additionalAccess,
        AccessVerificationDto verification) {
}
