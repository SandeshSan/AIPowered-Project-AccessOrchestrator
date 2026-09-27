package com.accessorchestrator.service;

import com.accessorchestrator.dto.EntitlementDto;
import com.accessorchestrator.dto.ProjectAccessDto;
import com.accessorchestrator.dto.UserAccessDto;

import java.util.Comparator;

/** Stable ordering so API output (and anything the LLM later explains) is deterministic. */
final class AccessOrdering {

    static final Comparator<EntitlementDto> ENTITLEMENT =
            Comparator.comparing(EntitlementDto::application).thenComparing(EntitlementDto::entitlementCode);

    static final Comparator<ProjectAccessDto> PROJECT_ACCESS =
            Comparator.comparing(ProjectAccessDto::entitlement, ENTITLEMENT);

    static final Comparator<UserAccessDto> USER_ACCESS =
            Comparator.comparing(UserAccessDto::entitlement, ENTITLEMENT);

    private AccessOrdering() {
    }
}
