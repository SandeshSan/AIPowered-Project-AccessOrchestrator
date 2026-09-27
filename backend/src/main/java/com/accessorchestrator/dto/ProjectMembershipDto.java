package com.accessorchestrator.dto;

import java.time.LocalDate;

/** A project the user is assigned to, with their access coverage for it. */
public record ProjectMembershipDto(
        ProjectDto project,
        String projectRole,
        LocalDate joinedDate,
        AccessComparisonSummary access) {
}
