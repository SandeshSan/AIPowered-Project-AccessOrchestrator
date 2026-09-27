package com.accessorchestrator.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Payloads for removing someone from a project. */
public final class RemovalDtos {

    private RemovalDtos() {
    }

    /** One entitlement that will be revoked, with why it belonged to the project. */
    public record RevocationItemDto(EntitlementDto entitlement, String reason, LocalDate grantedDate, String source) {
    }

    /**
     * What removing {@code employee} from {@code project} would do. Computed entirely in Java.
     *
     * @param authorityBasis   why the actor may do this, e.g. "direct report" or "self-removal"
     * @param requestsToCancel grant requests for this project that will be withdrawn
     * @param blockers         reasons the removal cannot run yet (empty when {@code canProceed})
     */
    public record RemovalPreviewDto(
            UserDto employee,
            ProjectDto project,
            String projectRole,
            LocalDate joinedDate,
            boolean selfRemoval,
            String authorityBasis,
            List<RevocationItemDto> toRevoke,
            List<UserAccessDto> keptDefault,
            List<AccessRequestDto> requestsToCancel,
            List<String> blockers,
            boolean canProceed) {
    }

    /** @param revocationRequest null when the person held no revocable access for the project */
    public record RemovalResultDto(
            UserDto employee,
            ProjectDto project,
            String reason,
            String removedBy,
            Instant removedAt,
            AccessRequestDto revocationRequest,
            List<AccessRequestDto> cancelledRequests,
            List<UserAccessDto> keptDefault) {
    }

    public record ProjectMemberDto(UserDto user, String projectRole, LocalDate joinedDate) {
    }

    /** Someone in the actor's reporting line. @param level 1 = direct report, 2 = their report, ... */
    public record ReportDto(UserDto user, int level, List<ProjectDto> projects) {
    }

    /** A reportee as shown on the manager's "My Team" page. @param level 1 = direct report */
    public record TeamMemberDto(UserDto user, int level, List<ProjectDto> projects, List<UserAccessDto> activeAccess) {
    }

    /** What the signed-in user may do beyond their own access: admin rights and people who report to them. */
    public record CapabilitiesDto(boolean admin, List<ReportDto> reports) {
    }
}
