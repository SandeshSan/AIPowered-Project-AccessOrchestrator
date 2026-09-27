package com.accessorchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AccessAnalysisRequest(
        @NotBlank String userId,
        @NotNull @Positive Long projectId) {
}
