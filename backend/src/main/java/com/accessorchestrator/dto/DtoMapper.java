package com.accessorchestrator.dto;

import com.accessorchestrator.domain.AccessRequest;
import com.accessorchestrator.domain.AccessRequestItem;
import com.accessorchestrator.domain.Entitlement;
import com.accessorchestrator.domain.Project;
import com.accessorchestrator.domain.ProjectAccess;
import com.accessorchestrator.domain.User;
import com.accessorchestrator.domain.UserAccess;

/** Entity -> DTO mapping. Must be called inside a transaction when lazy associations are touched. */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static UserDto toDto(User u) {
        User m = u.getManager();
        return new UserDto(u.getUserId(), u.getName(), u.getEmail(), u.getRole(), u.getDepartment(), u.getStatus(),
                u.isAdmin(), m == null ? null : m.getUserId(), m == null ? null : m.getName());
    }

    public static ProjectDto toDto(Project p) {
        return new ProjectDto(p.getId(), p.getProjectCode(), p.getProjectName(), p.getDescription(), p.getStatus());
    }

    public static EntitlementDto toDto(Entitlement e) {
        return new EntitlementDto(e.getId(), e.getApplication(), e.getEntitlementCode(), e.getEntitlementName(),
                e.getDescription(), e.getEnvironment(), e.getRiskLevel());
    }

    public static ProjectAccessDto toDto(ProjectAccess pa) {
        return new ProjectAccessDto(toDto(pa.getEntitlement()), pa.isRequired(), pa.getReason());
    }

    public static UserAccessDto toDto(UserAccess ua) {
        return new UserAccessDto(toDto(ua.getEntitlement()), ua.getStatus(), ua.getGrantedDate(), ua.getSource());
    }

    public static AccessRequestItemDto toDto(AccessRequestItem i) {
        return new AccessRequestItemDto(toDto(i.getEntitlement()), i.getStatus(), i.getReason());
    }

    public static AccessRequestDto toDto(AccessRequest r) {
        return new AccessRequestDto(r.getRequestId(), r.getType(), r.getUser().getUserId(), r.getProject().getId(),
                r.getProject().getProjectName(), r.getStatus(), r.getIgaRequestId(), r.getCreatedAt(),
                r.getUpdatedAt(), r.getCreatedBy(), r.getJustification(),
                r.getItems().stream().map(DtoMapper::toDto).toList(),
                RequestLifecycle.of(r.getStatus(), r.getType()));
    }
}
