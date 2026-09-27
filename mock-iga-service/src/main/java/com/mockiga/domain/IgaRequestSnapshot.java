package com.mockiga.domain;

import java.time.Instant;
import java.util.List;

/** Immutable, consistent copy of an {@link IgaRequest} taken under its lock. */
public record IgaRequestSnapshot(
        String requestId,
        IgaRequestType type,
        String userId,
        List<String> entitlements,
        String externalReference,
        String requestedBy,
        String justification,
        IgaRequestStatus status,
        Instant createdAt,
        Instant updatedAt,
        String decidedBy,
        String decisionComment,
        List<StatusEvent> history) {
}
