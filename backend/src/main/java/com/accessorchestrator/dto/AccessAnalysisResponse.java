package com.accessorchestrator.dto;

import java.util.List;

/**
 * Result of the deterministic gap analysis: missingAccess = requiredAccess - existingAccess.
 */
public record AccessAnalysisResponse(
        String userId,
        String userName,
        Long projectId,
        String project,
        List<ProjectAccessDto> requiredAccess,
        List<UserAccessDto> existingAccess,
        List<ProjectAccessDto> missingAccess) {
}
