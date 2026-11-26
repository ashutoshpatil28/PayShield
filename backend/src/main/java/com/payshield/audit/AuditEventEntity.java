package com.payshield.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

    @Id
    private UUID id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(name = "actor_roles")
    private String actorRoles;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    @Column(name = "resource_id", length = 128)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditOutcome outcome;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(UUID id, UUID actorUserId, String actorEmail, String actorRoles,
                            String action, String resourceType, String resourceId,
                            AuditOutcome outcome, String correlationId, String ipAddress,
                            String detailsJson, Instant createdAt) {
        this.id = id;
        this.actorUserId = actorUserId;
        this.actorEmail = actorEmail;
        this.actorRoles = actorRoles;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.outcome = outcome;
        this.correlationId = correlationId;
        this.ipAddress = ipAddress;
        this.detailsJson = detailsJson;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getActorUserId() { return actorUserId; }
    public String getActorEmail() { return actorEmail; }
    public String getActorRoles() { return actorRoles; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public AuditOutcome getOutcome() { return outcome; }
    public String getCorrelationId() { return correlationId; }
    public String getIpAddress() { return ipAddress; }
    public String getDetailsJson() { return detailsJson; }
    public Instant getCreatedAt() { return createdAt; }
}
