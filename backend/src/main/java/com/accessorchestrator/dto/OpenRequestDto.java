package com.accessorchestrator.dto;

import com.accessorchestrator.domain.RequestStatus;

/** An entitlement the user has already requested and whose request is still in progress at the IGA. */
public record OpenRequestDto(
        Long entitlementId,
        String entitlementCode,
        String requestId,
        String igaRequestId,
        RequestStatus status) {
}
