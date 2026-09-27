package com.mockiga.api;

import com.mockiga.domain.IgaRequestSnapshot;
import com.mockiga.domain.IgaRequestStatus;
import com.mockiga.domain.IgaRequestType;
import com.mockiga.domain.Lifecycle;
import com.mockiga.domain.StatusEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Request/response payloads of the Mock IGA REST API. */
public final class ApiModels {

    private ApiModels() {
    }

    /**
     * @param type              GRANT (default) or REVOKE
     * @param externalReference caller's own id for the request (e.g. the orchestrator's REQ-20260926-AB12CD34)
     */
    public record CreateRequest(
            IgaRequestType type,
            @NotBlank @Size(max = 64) String userId,
            @NotEmpty List<@NotBlank String> entitlements,
            @Size(max = 64) String externalReference,
            @Size(max = 64) String requestedBy,
            @Size(max = 1000) String justification) {
    }

    public record CreateResponse(String requestId, IgaRequestStatus status) {
    }

    /** Body for approve/reject/provision; every field is optional. */
    public record ActionRequest(@Size(max = 64) String actor, @Size(max = 1000) String comment) {
    }

    public record RequestView(
            String requestId,
            IgaRequestType type,
            String userId,
            List<String> entitlements,
            String externalReference,
            String requestedBy,
            String justification,
            IgaRequestStatus status,
            String statusLabel,
            Instant createdAt,
            Instant updatedAt,
            String decidedBy,
            String decisionComment,
            List<StatusEvent> history,
            List<Lifecycle.Stage> lifecycle) {

        static RequestView of(IgaRequestSnapshot s) {
            return new RequestView(s.requestId(), s.type(), s.userId(), s.entitlements(), s.externalReference(),
                    s.requestedBy(), s.justification(), s.status(), Lifecycle.label(s.status(), s.type()), s.createdAt(),
                    s.updatedAt(), s.decidedBy(), s.decisionComment(), s.history(), Lifecycle.of(s));
        }
    }
}
