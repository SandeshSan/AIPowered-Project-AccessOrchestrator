package com.accessorchestrator.dto;

import com.accessorchestrator.domain.AccessSource;
import com.accessorchestrator.domain.AccessStatus;

import java.time.LocalDate;

public record UserAccessDto(
        EntitlementDto entitlement,
        AccessStatus status,
        LocalDate grantedDate,
        AccessSource source) {
}
