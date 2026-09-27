package com.accessorchestrator.dto;

/**
 * Evidence that the gap was computed correctly, e.g. "5 required - 2 already granted = 3 missing".
 * The service refuses to return a comparison whose verification fails, so {@code verified} is always true
 * in a successful response.
 */
public record AccessVerificationDto(
        String rule,
        String expression,
        int requiredCount,
        int grantedCount,
        int missingCount,
        boolean verified) {
}
