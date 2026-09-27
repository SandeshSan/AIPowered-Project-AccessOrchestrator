package com.accessorchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

/** Employee identity. Table is "app_user" because "user" is reserved in PostgreSQL. */
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Corporate employee id, e.g. NT10036. */
    @Column(name = "user_id", nullable = false, unique = true, length = 32)
    private String userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    private String role;

    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    /** Application administrator (e.g. may browse the project access catalog). Not a job role. */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean admin;

    /** Encoded sign-in password ({@code {bcrypt}...}). Null means the user cannot sign in. */
    @Column(name = "password_hash")
    private String passwordHash;

    /** Line manager (reporting line). Null for the top of the organisation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    protected User() {
    }

    public User(String userId, String name, String email, String role, String department, UserStatus status) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.department = department;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getDepartment() { return department; }
    public UserStatus getStatus() { return status; }

    public boolean isAdmin() { return admin; }
    public User getManager() { return manager; }

    public void setStatus(UserStatus status) { this.status = status; }
    public void setAdmin(boolean admin) { this.admin = admin; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    /** Sets the line manager; rejects managing yourself and loops in the reporting line. */
    public void setManager(User newManager) {
        for (User m = newManager; m != null; m = m.getManager()) {
            if (m == this || (m.getUserId() != null && m.getUserId().equalsIgnoreCase(userId))) {
                throw new IllegalArgumentException(userId + " cannot report to " + newManager.getUserId()
                        + ": that would create a loop in the reporting line");
            }
        }
        this.manager = newManager;
    }
}
