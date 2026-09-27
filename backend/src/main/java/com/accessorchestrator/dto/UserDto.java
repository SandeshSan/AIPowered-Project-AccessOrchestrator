package com.accessorchestrator.dto;

import com.accessorchestrator.domain.UserStatus;

public record UserDto(
        String userId,
        String name,
        String email,
        String role,
        String department,
        UserStatus status,
        boolean admin,
        String managerId,
        String managerName) {
}
