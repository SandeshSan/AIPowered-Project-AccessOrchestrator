package com.accessorchestrator.iga;

import com.accessorchestrator.domain.RequestStatus;

/** Acknowledgement from the IGA system after a request is submitted. */
public record AccessRequestResponse(
        String igaRequestId,
        RequestStatus status,
        String message) {
}
