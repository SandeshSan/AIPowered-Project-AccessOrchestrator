package com.accessorchestrator.service;

import com.accessorchestrator.dto.AccessComparisonItemDto;
import com.accessorchestrator.dto.AccessComparisonSummary;
import com.accessorchestrator.dto.AccessVerificationDto;
import com.accessorchestrator.dto.ComparisonStatus;
import com.accessorchestrator.dto.ProjectAccessDto;
import com.accessorchestrator.dto.UserAccessDto;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pure, side-effect-free access set arithmetic. All inputs are already-filtered DTOs:
 * {@code required} = mandatory project entitlements, {@code existing} = the user's ACTIVE entitlements.
 * <p>
 * Entitlements are matched by id; codes are unique too, and the verifier uses codes so it is an
 * independent second computation rather than a re-run of the same filter.
 */
final class AccessGapCalculator {

    private static final String RULE = "Required - Existing = Missing";

    private AccessGapCalculator() {
    }

    /** missing = required \ existing, in {@link AccessOrdering} order. */
    static List<ProjectAccessDto> missing(Collection<ProjectAccessDto> required,
                                          Collection<UserAccessDto> existing) {
        Set<Long> held = existing.stream()
                .map(ua -> ua.entitlement().id())
                .collect(Collectors.toUnmodifiableSet());
        return required.stream()
                .filter(pa -> !held.contains(pa.entitlement().id()))
                .sorted(AccessOrdering.PROJECT_ACCESS)
                .toList();
    }

    /** One row per required entitlement, marked GRANTED (with grant details) or MISSING. */
    static List<AccessComparisonItemDto> compareItems(Collection<ProjectAccessDto> required,
                                                      Collection<UserAccessDto> existing) {
        Map<Long, UserAccessDto> heldById = existing.stream()
                .collect(Collectors.toMap(ua -> ua.entitlement().id(), Function.identity(), (a, b) -> a));
        return required.stream()
                .sorted(AccessOrdering.PROJECT_ACCESS)
                .map(pa -> {
                    UserAccessDto held = heldById.get(pa.entitlement().id());
                    return held == null
                            ? new AccessComparisonItemDto(pa.entitlement(), pa.reason(), ComparisonStatus.MISSING,
                            null, null)
                            : new AccessComparisonItemDto(pa.entitlement(), pa.reason(), ComparisonStatus.GRANTED,
                            held.grantedDate(), held.source());
                })
                .toList();
    }

    /** Access the user holds that the project does not require (informational; never revoked by us). */
    static List<UserAccessDto> additional(Collection<ProjectAccessDto> required,
                                          Collection<UserAccessDto> existing) {
        Set<Long> requiredIds = required.stream()
                .map(pa -> pa.entitlement().id())
                .collect(Collectors.toUnmodifiableSet());
        return existing.stream()
                .filter(ua -> !requiredIds.contains(ua.entitlement().id()))
                .sorted(AccessOrdering.USER_ACCESS)
                .toList();
    }

    static AccessComparisonSummary summarize(List<AccessComparisonItemDto> items, int additionalCount) {
        int requiredCount = items.size();
        int missingCount = (int) items.stream().filter(i -> i.status() == ComparisonStatus.MISSING).count();
        int grantedCount = requiredCount - missingCount;
        int coverage = requiredCount == 0 ? 100 : (int) Math.floor(grantedCount * 100.0 / requiredCount);
        return new AccessComparisonSummary(requiredCount, grantedCount, missingCount, additionalCount, coverage,
                missingCount == 0);
    }

    /**
     * Independently re-derives {@code required \ existing} on entitlement codes and checks it equals
     * {@code missing}, that missing and existing are disjoint, and that granted + missing = required.
     */
    static AccessVerificationDto verify(Collection<ProjectAccessDto> required,
                                        Collection<UserAccessDto> existing,
                                        Collection<ProjectAccessDto> missing) {
        Set<String> requiredCodes = codes(required);
        Set<String> existingCodes = existing.stream()
                .map(ua -> ua.entitlement().entitlementCode())
                .collect(Collectors.toSet());
        Set<String> missingCodes = codes(missing);

        Set<String> expectedMissing = new HashSet<>(requiredCodes);
        expectedMissing.removeAll(existingCodes);

        Set<String> grantedCodes = new HashSet<>(requiredCodes);
        grantedCodes.retainAll(existingCodes);

        boolean noDuplicates = missingCodes.size() == missing.size() && requiredCodes.size() == required.size();
        boolean disjoint = missingCodes.stream().noneMatch(existingCodes::contains);
        boolean verified = noDuplicates && disjoint
                && expectedMissing.equals(missingCodes)
                && grantedCodes.size() + missingCodes.size() == requiredCodes.size();

        String expression = "%d required - %d already granted = %d missing"
                .formatted(requiredCodes.size(), grantedCodes.size(), missingCodes.size());
        return new AccessVerificationDto(RULE, expression, requiredCodes.size(), grantedCodes.size(),
                missingCodes.size(), verified);
    }

    private static Set<String> codes(Collection<ProjectAccessDto> access) {
        return access.stream().map(pa -> pa.entitlement().entitlementCode()).collect(Collectors.toSet());
    }
}
