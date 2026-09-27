package com.accessorchestrator.dto;

import java.util.List;

/**
 * Everything the dashboard shows for one user, in one call.
 *
 * @param missingAccessCount           distinct required entitlements missing across the user's projects
 * @param missingAlreadyRequestedCount of those, how many are already in an in-progress request
 * @param recentlyProvisioned          ACTIVE access granted in the last 30 days, newest first
 */
public record DashboardDto(
        UserDto user,
        int activeAccessCount,
        int pendingRequestCount,
        int projectCount,
        int missingAccessCount,
        int missingAlreadyRequestedCount,
        List<ProjectMembershipDto> projects,
        List<UserAccessDto> activeAccess,
        List<AccessRequestDto> pendingRequests,
        List<UserAccessDto> recentlyProvisioned) {
}
