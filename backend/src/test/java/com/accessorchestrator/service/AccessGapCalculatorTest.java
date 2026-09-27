package com.accessorchestrator.service;

import com.accessorchestrator.domain.AccessSource;
import com.accessorchestrator.domain.AccessStatus;
import com.accessorchestrator.domain.RiskLevel;
import com.accessorchestrator.dto.AccessComparisonItemDto;
import com.accessorchestrator.dto.AccessComparisonSummary;
import com.accessorchestrator.dto.AccessVerificationDto;
import com.accessorchestrator.dto.ComparisonStatus;
import com.accessorchestrator.dto.EntitlementDto;
import com.accessorchestrator.dto.ProjectAccessDto;
import com.accessorchestrator.dto.UserAccessDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the pure set arithmetic: Required - Existing = Missing. */
class AccessGapCalculatorTest {

    private static final EntitlementDto GITHUB = ent(1, "GitHub", "NOVATECH_DEV");
    private static final EntitlementDto GCP = ent(2, "GCP", "GCP_NOVATECH_DEV");
    private static final EntitlementDto JIRA = ent(3, "Jira", "NOVATECH_JIRA");
    private static final EntitlementDto DB = ent(4, "Database", "NOVATECH_DB_READ");
    private static final EntitlementDto VPN = ent(5, "VPN", "NOVATECH_VPN");
    private static final EntitlementDto UNRELATED = ent(99, "GitHub", "OTHER_PROJECT");

    private static final List<ProjectAccessDto> NOVATECH_REQUIRED =
            List.of(req(VPN), req(GITHUB), req(DB), req(GCP), req(JIRA));
    private static final List<UserAccessDto> JOHN_EXISTING = List.of(held(GITHUB), held(GCP));

    // --- missing -------------------------------------------------------------------------------------

    @Test
    void missingIsRequiredMinusExisting() {
        assertThat(codes(AccessGapCalculator.missing(NOVATECH_REQUIRED, JOHN_EXISTING)))
                .containsExactly("NOVATECH_DB_READ", "NOVATECH_JIRA", "NOVATECH_VPN");
    }

    @Test
    void everythingIsMissingWhenUserHasNoAccess() {
        assertThat(AccessGapCalculator.missing(NOVATECH_REQUIRED, List.of())).hasSize(5);
    }

    @Test
    void nothingIsMissingWhenUserHoldsEverything() {
        List<UserAccessDto> all = List.of(held(GITHUB), held(GCP), held(JIRA), held(DB), held(VPN));
        assertThat(AccessGapCalculator.missing(NOVATECH_REQUIRED, all)).isEmpty();
    }

    @Test
    void nothingIsMissingWhenProjectRequiresNothing() {
        assertThat(AccessGapCalculator.missing(List.of(), JOHN_EXISTING)).isEmpty();
    }

    @Test
    void unrelatedExistingAccessDoesNotAffectResult() {
        assertThat(codes(AccessGapCalculator.missing(NOVATECH_REQUIRED, List.of(held(UNRELATED), held(GITHUB)))))
                .containsExactly("NOVATECH_DB_READ", "GCP_NOVATECH_DEV", "NOVATECH_JIRA", "NOVATECH_VPN");
    }

    @Test
    void resultOrderIsIndependentOfInputOrder() {
        List<ProjectAccessDto> shuffled = List.of(req(JIRA), req(GCP), req(DB), req(GITHUB), req(VPN));
        assertThat(AccessGapCalculator.missing(shuffled, List.of()))
                .isEqualTo(AccessGapCalculator.missing(NOVATECH_REQUIRED, List.of()));
    }

    // --- comparison ----------------------------------------------------------------------------------

    @Test
    void compareItemsMarksEachRequiredEntitlement() {
        List<AccessComparisonItemDto> items = AccessGapCalculator.compareItems(NOVATECH_REQUIRED, JOHN_EXISTING);

        assertThat(items).extracting(i -> i.entitlement().entitlementCode() + "=" + i.status())
                .containsExactly("NOVATECH_DB_READ=MISSING", "GCP_NOVATECH_DEV=GRANTED", "NOVATECH_DEV=GRANTED",
                        "NOVATECH_JIRA=MISSING", "NOVATECH_VPN=MISSING");
        assertThat(items).filteredOn(i -> i.status() == ComparisonStatus.GRANTED)
                .allSatisfy(i -> assertThat(i.source()).isEqualTo(AccessSource.IGA));
        assertThat(items).filteredOn(i -> i.status() == ComparisonStatus.MISSING)
                .allSatisfy(i -> assertThat(i.grantedDate()).isNull());
    }

    @Test
    void additionalIsExistingMinusRequired() {
        List<UserAccessDto> additional = AccessGapCalculator.additional(NOVATECH_REQUIRED,
                List.of(held(UNRELATED), held(GITHUB)));

        assertThat(additional).extracting(ua -> ua.entitlement().entitlementCode()).containsExactly("OTHER_PROJECT");
    }

    @Test
    void summaryCountsAndCoverage() {
        AccessComparisonSummary summary = AccessGapCalculator.summarize(
                AccessGapCalculator.compareItems(NOVATECH_REQUIRED, JOHN_EXISTING), 1);

        assertThat(summary).isEqualTo(new AccessComparisonSummary(5, 2, 3, 1, 40, false));
    }

    @Test
    void emptyProjectIsFullyProvisioned() {
        assertThat(AccessGapCalculator.summarize(List.of(), 0))
                .isEqualTo(new AccessComparisonSummary(0, 0, 0, 0, 100, true));
    }

    // --- verification --------------------------------------------------------------------------------

    @Test
    void verificationPassesForCorrectGap() {
        List<ProjectAccessDto> missing = AccessGapCalculator.missing(NOVATECH_REQUIRED, JOHN_EXISTING);
        AccessVerificationDto v = AccessGapCalculator.verify(NOVATECH_REQUIRED, JOHN_EXISTING, missing);

        assertThat(v.verified()).isTrue();
        assertThat(v.expression()).isEqualTo("5 required - 2 already granted = 3 missing");
    }

    @Test
    void verificationFailsWhenMissingOmitsAnEntitlement() {
        List<ProjectAccessDto> tooFew = List.of(req(DB), req(JIRA));
        assertThat(AccessGapCalculator.verify(NOVATECH_REQUIRED, JOHN_EXISTING, tooFew).verified()).isFalse();
    }

    @Test
    void verificationFailsWhenMissingIncludesHeldAccess() {
        List<ProjectAccessDto> includesHeld = List.of(req(DB), req(JIRA), req(VPN), req(GITHUB));
        assertThat(AccessGapCalculator.verify(NOVATECH_REQUIRED, JOHN_EXISTING, includesHeld).verified()).isFalse();
    }

    @Test
    void verificationFailsOnDuplicates() {
        List<ProjectAccessDto> duplicated = List.of(req(DB), req(JIRA), req(VPN), req(VPN));
        assertThat(AccessGapCalculator.verify(NOVATECH_REQUIRED, JOHN_EXISTING, duplicated).verified()).isFalse();
    }

    /** Property check: for many random profiles/holdings, missing equals the set difference exactly. */
    @Test
    void requiredMinusExistingEqualsMissingForRandomInputs() {
        Random random = new Random(42);
        List<EntitlementDto> universe = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> ent(i, "App" + (i % 4), "ENT_" + i))
                .toList();

        for (int run = 0; run < 1_000; run++) {
            List<ProjectAccessDto> required = randomSubset(universe, random).stream()
                    .map(AccessGapCalculatorTest::req).toList();
            List<UserAccessDto> existing = randomSubset(universe, random).stream()
                    .map(AccessGapCalculatorTest::held).toList();

            List<ProjectAccessDto> missing = AccessGapCalculator.missing(required, existing);

            Set<String> expected = new HashSet<>(codes(required));
            expected.removeAll(existing.stream().map(ua -> ua.entitlement().entitlementCode()).toList());

            assertThat(new HashSet<>(codes(missing))).as("run %d", run).isEqualTo(expected);
            assertThat(missing).as("run %d no duplicates", run).doesNotHaveDuplicates();
            assertThat(AccessGapCalculator.verify(required, existing, missing).verified()).as("run %d", run).isTrue();

            AccessComparisonSummary s = AccessGapCalculator.summarize(
                    AccessGapCalculator.compareItems(required, existing), 0);
            assertThat(s.grantedCount() + s.missingCount()).isEqualTo(s.requiredCount());
            assertThat(s.missingCount()).isEqualTo(missing.size());
        }
    }

    private static List<EntitlementDto> randomSubset(List<EntitlementDto> universe, Random random) {
        List<EntitlementDto> copy = new ArrayList<>(universe);
        Collections.shuffle(copy, random);
        return copy.subList(0, random.nextInt(copy.size() + 1));
    }

    private static List<String> codes(List<ProjectAccessDto> access) {
        return access.stream().map(pa -> pa.entitlement().entitlementCode()).collect(Collectors.toList());
    }

    private static EntitlementDto ent(long id, String app, String code) {
        return new EntitlementDto(id, app, code, code, null, "DEV", RiskLevel.LOW);
    }

    private static ProjectAccessDto req(EntitlementDto e) {
        return new ProjectAccessDto(e, true, "needed");
    }

    private static UserAccessDto held(EntitlementDto e) {
        return new UserAccessDto(e, AccessStatus.ACTIVE, LocalDate.of(2026, 1, 1), AccessSource.IGA);
    }
}
