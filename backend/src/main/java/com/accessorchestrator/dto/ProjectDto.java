package com.accessorchestrator.dto;

import com.accessorchestrator.domain.ProjectStatus;

public record ProjectDto(
        Long id,
        String projectCode,
        String projectName,
        String description,
        ProjectStatus status) {
}
