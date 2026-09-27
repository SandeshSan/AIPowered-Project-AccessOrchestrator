package com.accessorchestrator.iga;

import com.accessorchestrator.domain.RequestStatus;

import java.time.Instant;

/** Current state of a request inside the IGA system, mapped to our domain status. */
public record AccessRequestStatus(
        String igaRequestId,
        RequestStatus status,
        Instant updatedAt,
        String comment) {
}
