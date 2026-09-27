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
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Which projects an employee is assigned to (drives "My projects" and missing-access totals). Removal ends the
 * membership but keeps the row, with who removed it, when and why.
 */
@Entity
@Table(name = "project_member",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "project_id"}))
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "project_role", length = 64)
    private String projectRole;

    @Column(name = "joined_date")
    private LocalDate joinedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @ColumnDefault("'ACTIVE'")
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by", length = 64)
    private String removedBy;

    @Column(name = "removal_reason", length = 1000)
    private String removalReason;

    protected ProjectMember() {
    }

    public ProjectMember(User user, Project project, String projectRole, LocalDate joinedDate) {
        this.user = user;
        this.project = project;
        this.projectRole = projectRole;
        this.joinedDate = joinedDate;
    }

    /** Ends the membership for audit instead of deleting it. */
    public void end(String removedByUserId, String reason) {
        this.status = MembershipStatus.REMOVED;
        this.removedAt = Instant.now();
        this.removedBy = removedByUserId;
        this.removalReason = reason;
    }

    /** Re-activates a previously ended membership (the row is kept, one per user and project). */
    public void rejoin(String role, LocalDate joinedOn) {
        this.status = MembershipStatus.ACTIVE;
        this.projectRole = role;
        this.joinedDate = joinedOn;
        this.removedAt = null;
        this.removedBy = null;
        this.removalReason = null;
    }

    public boolean isActive() { return status == MembershipStatus.ACTIVE; }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Project getProject() { return project; }
    public String getProjectRole() { return projectRole; }
    public LocalDate getJoinedDate() { return joinedDate; }
    public MembershipStatus getStatus() { return status; }
    public Instant getRemovedAt() { return removedAt; }
    public String getRemovedBy() { return removedBy; }
    public String getRemovalReason() { return removalReason; }
}
