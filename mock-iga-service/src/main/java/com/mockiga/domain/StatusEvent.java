package com.mockiga.domain;

import java.time.Instant;

/** One entry in a request's audit trail. */
public record StatusEvent(IgaRequestStatus status, Instant at, String actor, String comment) {
}
