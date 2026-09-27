package com.accessorchestrator.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "access_request")
public class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Business key exposed to clients, e.g. REQ-2026-000001-ab12cd. */
    @Column(name = "request_id", nullable = false, unique = true, length = 64)
    private String requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @ColumnDefault("'GRANT'")
    private RequestType type = RequestType.GRANT;

    /** Why the request exists (e.g. the reason for a removal); sent to the IGA. */
    @Column(length = 1000)
    private String justification;

    /** Reference returned by the IGA system once submitted (null while DRAFT). */
    @Column(name = "iga_request_id", length = 128)
    private String igaRequestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    /** Optimistic lock: the status poller and a manual sync must not both apply the same IGA update. */
    @Version
    private Long version;

    @OneToMany(mappedBy = "accessRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<AccessRequestItem> items = new ArrayList<>();

    protected AccessRequest() {
    }

    public AccessRequest(String requestId, User user, Project project, RequestStatus status, String createdBy) {
        this(requestId, RequestType.GRANT, user, project, status, createdBy);
    }

    public AccessRequest(String requestId, RequestType type, User user, Project project, RequestStatus status,
                         String createdBy) {
        this.requestId = requestId;
        this.type = type;
        this.user = user;
        this.project = project;
        this.status = status;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            this.createdAt = now;
        }
        if (updatedAt == null) {
            this.updatedAt = now;
        }
    }

    /** For seeding or importing historical requests: sets timestamps before the first save. */
    public void backdate(Instant created, Instant updated) {
        this.createdAt = created;
        this.updatedAt = updated;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void addItem(AccessRequestItem item) {
        item.setAccessRequest(this);
        items.add(item);
    }

    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public User getUser() { return user; }
    public Project getProject() { return project; }
    public RequestStatus getStatus() { return status; }
    public RequestType getType() { return type; }
    public String getJustification() { return justification; }
    public String getIgaRequestId() { return igaRequestId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public List<AccessRequestItem> getItems() { return Collections.unmodifiableList(items); }

    public void setStatus(RequestStatus status) { this.status = status; }
    public void setIgaRequestId(String igaRequestId) { this.igaRequestId = igaRequestId; }
    public void setJustification(String justification) { this.justification = justification; }
}
