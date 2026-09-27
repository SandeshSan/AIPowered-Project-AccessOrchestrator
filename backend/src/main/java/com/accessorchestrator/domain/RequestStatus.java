package com.accessorchestrator.domain;

/**
 * Orchestrator-side request status. After submission it mirrors the IGA's status:
 * DRAFT -> SUBMITTED -> PENDING_APPROVAL -> APPROVED -> PROVISIONING -> PROVISIONED (or REJECTED / FAILED).
 */
public enum RequestStatus {
    DRAFT, SUBMITTED, PENDING_APPROVAL, APPROVED, REJECTED, PROVISIONING, PROVISIONED, FAILED, CANCELLED;

    public boolean isTerminal() {
        return this == REJECTED || this == PROVISIONED || this == FAILED || this == CANCELLED;
    }
}
