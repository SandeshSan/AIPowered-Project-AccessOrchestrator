package com.accessorchestrator.service;

import com.accessorchestrator.domain.AccessSource;
import com.accessorchestrator.domain.AccessStatus;
import com.accessorchestrator.domain.RiskLevel;
import com.accessorchestrator.dto.EntitlementDto;
import com.accessorchestrator.dto.ProjectAccessDto;
import com.accessorchestrator.dto.UserAccessDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** revoke = (active access ∩ project profile) − DEFAULT access. */
class RevocationCalculatorTest {

    private static final EntitlementDto GITHUB = ent(1, "GitHub", "NOVATECH_DEV");
    private static final EntitlementDto JIRA = ent(3, "Jira", "NOVATECH_JIRA");
    private static final EntitlementDto WIKI = ent(6, "Confluence", "NOVATECH_WIKI");
    private static final EntitlementDto ORION = ent(7, "GitHub", "ORION_DEV");
    private static final EntitlementDto JIRA_USER = ent(8, "Jira", "JIRA_USER");

    private static final List<ProjectAccessDto> NOVATECH = List.of(
            new ProjectAccessDto(GITHUB, true, "Required for source code development"),
            new ProjectAccessDto(JIRA, true, "Required to track work"),
            new ProjectAccessDto(WIKI, false, "Optional: documentation"),
            new ProjectAccessDto(JIRA_USER, true, "Company-wide Jira licence"));

    @Test
    void revokesProjectAccessIncludingOptionalEntries() {
        RevocationCalculator.Plan plan = RevocationCalculator.plan(NOVATECH,
                List.of(held(GITHUB, AccessSource.IGA), held(WIKI, AccessSource.MANUAL)));

        assertThat(plan.toRevoke()).extracting(r -> r.access().entitlement().entitlementCode())
                .containsExactly("NOVATECH_WIKI", "NOVATECH_DEV");
        assertThat(plan.toRevoke()).extracting(RevocationCalculator.Revocation::reason)
                .containsExactly("Optional: documentation", "Required for source code development");
        assertThat(plan.keptDefault()).isEmpty();
    }

    @Test
    void keepsDefaultAccess() {
        RevocationCalculator.Plan plan = RevocationCalculator.plan(NOVATECH,
                List.of(held(GITHUB, AccessSource.IGA), held(JIRA, AccessSource.IGA),
                        held(JIRA_USER, AccessSource.DEFAULT)));

        assertThat(plan.toRevoke()).extracting(r -> r.access().entitlement().entitlementCode())
                .containsExactly("NOVATECH_DEV", "NOVATECH_JIRA");
        assertThat(plan.keptDefault()).extracting(ua -> ua.entitlement().entitlementCode())
                .containsExactly("JIRA_USER");
    }

    @Test
    void ignoresAccessOutsideTheProject() {
        RevocationCalculator.Plan plan = RevocationCalculator.plan(NOVATECH, List.of(held(ORION, AccessSource.IGA)));

        assertThat(plan.toRevoke()).isEmpty();
        assertThat(plan.keptDefault()).isEmpty();
    }

    @Test
    void nothingHeldMeansNothingToRevoke() {
        assertThat(RevocationCalculator.plan(NOVATECH, List.of()).toRevoke()).isEmpty();
    }

    private static EntitlementDto ent(long id, String app, String code) {
        return new EntitlementDto(id, app, code, code, null, "DEV", RiskLevel.LOW);
    }

    private static UserAccessDto held(EntitlementDto e, AccessSource source) {
        return new UserAccessDto(e, AccessStatus.ACTIVE, LocalDate.of(2026, 1, 1), source);
    }
}
