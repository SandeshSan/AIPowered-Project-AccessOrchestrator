package com.mockiga.domain;

/**
 * Request lifecycle:
 * <pre>
 * CREATED -> PENDING_APPROVAL -> APPROVED -> PROVISIONING -> PROVISIONED
 *                            \-> REJECTED
 * PENDING_APPROVAL / APPROVED -> CANCELLED   (withdrawn by the requesting system)
 * </pre>
 * For REVOKE requests PROVISIONING/PROVISIONED mean deprovisioning/revoked.
 * CREATED is recorded in history only; a stored request is never left in that state.
 */
public enum IgaRequestStatus {
    CREATED, PENDING_APPROVAL, APPROVED, REJECTED, PROVISIONING, PROVISIONED, CANCELLED;

    public boolean isTerminal() {
        return this == REJECTED || this == PROVISIONED || this == CANCELLED;
    }
}
