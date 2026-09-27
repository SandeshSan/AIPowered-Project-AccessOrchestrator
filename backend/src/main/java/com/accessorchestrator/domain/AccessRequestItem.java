package com.accessorchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "access_request_item")
public class AccessRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private AccessRequest accessRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entitlement_id", nullable = false)
    private Entitlement entitlement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RequestItemStatus status;

    @Column(length = 1000)
    private String reason;

    protected AccessRequestItem() {
    }

    public AccessRequestItem(Entitlement entitlement, RequestItemStatus status, String reason) {
        this.entitlement = entitlement;
        this.status = status;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public AccessRequest getAccessRequest() { return accessRequest; }
    public Entitlement getEntitlement() { return entitlement; }
    public RequestItemStatus getStatus() { return status; }
    public String getReason() { return reason; }

    void setAccessRequest(AccessRequest accessRequest) { this.accessRequest = accessRequest; }
    public void setStatus(RequestItemStatus status) { this.status = status; }
}
