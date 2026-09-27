package com.accessorchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Standard access profile: which entitlements a project member needs, and why. */
@Entity
@Table(name = "project_access",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "entitlement_id"}))
public class ProjectAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entitlement_id", nullable = false)
    private Entitlement entitlement;

    /** false = recommended/optional for the project, true = mandatory. */
    @Column(nullable = false)
    private boolean required;

    @Column(length = 1000)
    private String reason;

    protected ProjectAccess() {
    }

    public ProjectAccess(Project project, Entitlement entitlement, boolean required, String reason) {
        this.project = project;
        this.entitlement = entitlement;
        this.required = required;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public Entitlement getEntitlement() { return entitlement; }
    public boolean isRequired() { return required; }
    public String getReason() { return reason; }
}
