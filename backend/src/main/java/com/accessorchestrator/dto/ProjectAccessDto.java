package com.accessorchestrator.dto;

/** One entry of a project's standard access profile. */
public record ProjectAccessDto(
        EntitlementDto entitlement,
        boolean required,
        String reason) {
}
