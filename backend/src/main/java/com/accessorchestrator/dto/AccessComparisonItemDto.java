package com.accessorchestrator.dto;

import com.accessorchestrator.domain.AccessSource;

import java.time.LocalDate;

/**
 * One required entitlement and whether the user holds it.
 *
 * @param grantedDate null when MISSING
 * @param source      null when MISSING
 */
public record AccessComparisonItemDto(
        EntitlementDto entitlement,
        String reason,
        ComparisonStatus status,
        LocalDate grantedDate,
        AccessSource source) {
}
