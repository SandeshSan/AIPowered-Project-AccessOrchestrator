package com.accessorchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_code", nullable = false, unique = true, length = 64)
    private String projectCode;

    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProjectStatus status;

    protected Project() {
    }

    public Project(String projectCode, String projectName, String description, ProjectStatus status) {
        this.projectCode = projectCode;
        this.projectName = projectName;
        this.description = description;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getProjectCode() { return projectCode; }
    public String getProjectName() { return projectName; }
    public String getDescription() { return description; }
    public ProjectStatus getStatus() { return status; }
}
