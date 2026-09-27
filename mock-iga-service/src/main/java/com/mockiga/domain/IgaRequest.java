package com.mockiga.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * An access request inside the mock IGA: a GRANT (provision entitlements) or a REVOKE (deprovision them).
 * All state changes go through the synchronized transition methods, which enforce the workflow so concurrent
 * approve/provision/cancel calls cannot corrupt it.
 */
public class IgaRequest {

    private final String requestId;
    private final IgaRequestType type;
    private final String userId;
    private final List<String> entitlements;
    private final String externalReference;
    private final String requestedBy;
    private final String justification;
    private final Instant createdAt;
    private final List<StatusEvent> history = new ArrayList<>();

    private IgaRequestStatus status;
    private Instant updatedAt;
    private String decidedBy;
    private String decisionComment;

    public IgaRequest(String requestId, IgaRequestType type, String userId, List<String> entitlements,
                      String externalReference, String requestedBy, String justification) {
        this.requestId = requestId;
        this.type = type;
        this.userId = userId;
        this.entitlements = List.copyOf(entitlements);
        this.externalReference = externalReference;
        this.requestedBy = requestedBy;
        this.justification = justification;
        this.createdAt = Instant.now();
        this.status = IgaRequestStatus.CREATED;
        record(IgaRequestStatus.CREATED, requestedBy,
                type == IgaRequestType.REVOKE ? "Revocation request received" : "Request received", createdAt);
        record(IgaRequestStatus.PENDING_APPROVAL, "system", "Routed to manager for approval", createdAt);
    }

    public synchronized void approve(String approver, String comment) {
        transition(EnumSet.of(IgaRequestStatus.PENDING_APPROVAL), IgaRequestStatus.APPROVED, approver, comment);
        this.decidedBy = approver;
        this.decisionComment = comment;
    }

    public synchronized void reject(String approver, String comment) {
        transition(EnumSet.of(IgaRequestStatus.PENDING_APPROVAL), IgaRequestStatus.REJECTED, approver, comment);
        this.decidedBy = approver;
        this.decisionComment = comment;
    }

    /** Withdrawn by the requesting system before anything was provisioned. */
    public synchronized void cancel(String actor, String comment) {
        transition(EnumSet.of(IgaRequestStatus.PENDING_APPROVAL, IgaRequestStatus.APPROVED), IgaRequestStatus.CANCELLED,
                actor, comment);
    }

    public synchronized void startProvisioning(String actor) {
        transition(EnumSet.of(IgaRequestStatus.APPROVED), IgaRequestStatus.PROVISIONING, actor,
                (type == IgaRequestType.REVOKE ? "Revoking " : "Provisioning ") + entitlements.size()
                        + " entitlement(s) in target systems");
    }

    public synchronized void completeProvisioning() {
        transition(EnumSet.of(IgaRequestStatus.PROVISIONING), IgaRequestStatus.PROVISIONED, "system",
                type == IgaRequestType.REVOKE ? "All entitlements revoked" : "All entitlements provisioned");
    }

    public synchronized IgaRequestStatus status() {
        return status;
    }

    public String requestId() {
        return requestId;
    }

    public synchronized IgaRequestSnapshot snapshot() {
        return new IgaRequestSnapshot(requestId, type, userId, entitlements, externalReference, requestedBy,
                justification, status, createdAt, updatedAt, decidedBy, decisionComment, List.copyOf(history));
    }

    private void transition(Set<IgaRequestStatus> allowedFrom, IgaRequestStatus to, String actor, String comment) {
        if (!allowedFrom.contains(status)) {
            throw new InvalidTransitionException(requestId, status, to);
        }
        record(to, actor, comment, Instant.now());
    }

    private void record(IgaRequestStatus to, String actor, String comment, Instant at) {
        this.status = to;
        this.updatedAt = at;
        history.add(new StatusEvent(to, at, actor, comment));
    }
}
