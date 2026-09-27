package com.accessorchestrator.dto;

import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.domain.RequestType;

import java.time.Instant;
import java.util.List;

/**
 * @param igaRequestId the IGA system's id (null while DRAFT)
 * @param lifecycle    stepper model for UIs, see {@link RequestLifecycle}
 */
public record AccessRequestDto(
        String requestId,
        RequestType type,
        String userId,
        Long projectId,
        String projectName,
        RequestStatus status,
        String igaRequestId,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String justification,
        List<AccessRequestItemDto> items,
        List<RequestLifecycle.StageDto> lifecycle) {
}
