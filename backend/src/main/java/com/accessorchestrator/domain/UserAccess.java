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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/** An entitlement a user currently holds (or held). Only ACTIVE rows count as existing access. */
@Entity
@Table(name = "user_access",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "entitlement_id"}))
public class UserAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entitlement_id", nullable = false)
    private Entitlement entitlement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccessStatus status;

    @Column(name = "granted_date")
    private LocalDate grantedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccessSource source;

    protected UserAccess() {
    }

    public UserAccess(User user, Entitlement entitlement, AccessStatus status, LocalDate grantedDate, AccessSource source) {
        this.user = user;
        this.entitlement = entitlement;
        this.status = status;
        this.grantedDate = grantedDate;
        this.source = source;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Entitlement getEntitlement() { return entitlement; }
    public AccessStatus getStatus() { return status; }
    public LocalDate getGrantedDate() { return grantedDate; }
    public AccessSource getSource() { return source; }

    public void setStatus(AccessStatus status) { this.status = status; }

    /** (Re)activate this entitlement, e.g. after the IGA reports it provisioned. */
    public void grant(LocalDate grantedOn, AccessSource grantedBy) {
        this.status = AccessStatus.ACTIVE;
        this.grantedDate = grantedOn;
        this.source = grantedBy;
    }
}
