package com.accessorchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A grantable permission in a target application (GitHub team, GCP role, VPN profile, ...). */
@Entity
@Table(name = "entitlement")
public class Entitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String application;

    @Column(name = "entitlement_code", nullable = false, unique = true, length = 128)
    private String entitlementCode;

    @Column(name = "entitlement_name", nullable = false)
    private String entitlementName;

    @Column(length = 1000)
    private String description;

    @Column(length = 32)
    private String environment;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private RiskLevel riskLevel;

    protected Entitlement() {
    }

    public Entitlement(String application, String entitlementCode, String entitlementName,
                       String description, String environment, RiskLevel riskLevel) {
        this.application = application;
        this.entitlementCode = entitlementCode;
        this.entitlementName = entitlementName;
        this.description = description;
        this.environment = environment;
        this.riskLevel = riskLevel;
    }

    public Long getId() { return id; }
    public String getApplication() { return application; }
    public String getEntitlementCode() { return entitlementCode; }
    public String getEntitlementName() { return entitlementName; }
    public String getDescription() { return description; }
    public String getEnvironment() { return environment; }
    public RiskLevel getRiskLevel() { return riskLevel; }
}
