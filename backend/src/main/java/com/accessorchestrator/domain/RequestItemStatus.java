package com.accessorchestrator.domain;

/** REVOKED = deprovisioned by a completed REVOKE request; CANCELLED = withdrawn before provisioning. */
public enum RequestItemStatus {
    PENDING, APPROVED, REJECTED, PROVISIONED, REVOKED, CANCELLED, FAILED
}
