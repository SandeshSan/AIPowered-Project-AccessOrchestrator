package com.accessorchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create-and-submit in one step (used by the AI agent after the user confirms).
 *
 * @param createdBy who initiated it, e.g. "access-agent" or the user id
 */
public record RequestAccessCommand(
        @NotBlank String userId,
        @NotNull @Positive Long projectId,
        @NotEmpty List<@NotNull Long> entitlementIds,
        @Size(max = 64) String createdBy) {
}
