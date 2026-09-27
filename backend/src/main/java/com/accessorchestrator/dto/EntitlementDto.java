package com.accessorchestrator.dto;

import com.accessorchestrator.domain.RiskLevel;

public record EntitlementDto(
        Long id,
        String application,
        String entitlementCode,
        String entitlementName,
        String description,
        String environment,
        RiskLevel riskLevel) {
}
