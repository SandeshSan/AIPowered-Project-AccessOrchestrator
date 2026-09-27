package com.accessorchestrator.dto;

import com.accessorchestrator.domain.RequestItemStatus;

public record AccessRequestItemDto(
        EntitlementDto entitlement,
        RequestItemStatus status,
        String reason) {
}
