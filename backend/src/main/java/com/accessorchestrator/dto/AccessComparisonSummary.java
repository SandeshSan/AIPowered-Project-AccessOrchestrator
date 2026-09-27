package com.accessorchestrator.dto;

/**
 * @param additionalCount    ACTIVE entitlements the user holds that this project does not require
 * @param coveragePercent    granted / required, floored; 100 when the project requires nothing
 * @param fullyProvisioned   true when nothing is missing
 */
public record AccessComparisonSummary(
        int requiredCount,
        int grantedCount,
        int missingCount,
        int additionalCount,
        int coveragePercent,
        boolean fullyProvisioned) {
}
