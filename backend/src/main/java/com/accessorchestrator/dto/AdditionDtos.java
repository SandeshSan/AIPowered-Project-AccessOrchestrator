package com.accessorchestrator.dto;

import java.util.List;

/** Payloads for adding someone to a project (membership only; no access is requested). */
public final class AdditionDtos {

    private AdditionDtos() {
    }

    /**
     * What adding {@code employee} to {@code project} would do. Computed entirely in Java.
     *
     * @param projectRole      role they would have on the project (defaults to their job title)
     * @param authorityBasis   why the actor may do this, e.g. "direct report"
     * @param alreadyHave      required entitlements they already hold
     * @param missingAccess    required entitlements they do not hold yet (information only: nothing is requested;
     *                         they can ask for it themselves)
     * @param alreadyRequested missing entitlements already in an in-progress request
     * @param blockers         why it cannot run (empty when {@code canProceed})
     */
    public record AdditionPreviewDto(
            UserDto employee,
            ProjectDto project,
            String projectRole,
            String authorityBasis,
            List<EntitlementDto> alreadyHave,
            List<ProjectAccessDto> missingAccess,
            List<OpenRequestDto> alreadyRequested,
            List<String> blockers,
            boolean canProceed) {
    }

    /** @param missingAccess required access they still need to request themselves */
    public record AdditionResultDto(
            UserDto employee,
            ProjectDto project,
            String projectRole,
            String addedBy,
            List<ProjectAccessDto> missingAccess) {
    }
}
