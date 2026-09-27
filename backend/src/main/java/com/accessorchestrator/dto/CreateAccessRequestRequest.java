package com.accessorchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Creates a DRAFT access request for the user's missing project access.
 *
 * @param entitlementCodes optional subset of the missing entitlements; when empty, all missing access is requested.
 *                         Codes that are not currently missing are rejected.
 * @param createdBy        who initiated the request; defaults to the user themself.
 */
public record CreateAccessRequestRequest(
        @NotBlank String userId,
        @NotNull @Positive Long projectId,
        List<@NotBlank String> entitlementCodes,
        @Size(max = 64) String createdBy) {
}
