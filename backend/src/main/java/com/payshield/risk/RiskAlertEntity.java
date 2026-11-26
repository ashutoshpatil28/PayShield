package com.payshield.risk;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_alerts")
public class RiskAlertEntity {

    public enum AlertStatus { OPEN, ACKNOWLEDGED, RESOLVED, DISMISSED }

    @Id
    private UUID id;

    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 128)
    private String entityId;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(nullable = false, length = 24)
    private String severity;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AlertStatus status;

    @Column(nullable = false, length = 500)
    private String explanation;

    @Column(name = "analyst_notes", length = 1000)
    private String analystNotes;

    @Column(name = "resolved_by", length = 200)
    private String resolvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected RiskAlertEntity() {}

    public RiskAlertEntity(UUID id, String ruleCode, String entityType, String entityId,
                           UUID merchantId, String severity, int riskScore,
                           String explanation, Instant createdAt) {
        this.id = id;
        this.ruleCode = ruleCode;
        this.entityType = entityType;
        this.entityId = entityId;
        this.merchantId = merchantId;
        this.severity = severity;
        this.riskScore = riskScore;
        this.status = AlertStatus.OPEN;
        this.explanation = explanation;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getRuleCode() { return ruleCode; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public UUID getMerchantId() { return merchantId; }
    public String getSeverity() { return severity; }
    public int getRiskScore() { return riskScore; }
    public AlertStatus getStatus() { return status; }
    public String getExplanation() { return explanation; }
    public String getAnalystNotes() { return analystNotes; }
    public String getResolvedBy() { return resolvedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }

    public void updateInvestigation(AlertStatus nextStatus, String notes, String actor) {
        this.status = nextStatus;
        this.analystNotes = notes;
        if (nextStatus == AlertStatus.RESOLVED || nextStatus == AlertStatus.DISMISSED) {
            this.resolvedBy = actor;
            this.resolvedAt = Instant.now();
        }
    }
}
