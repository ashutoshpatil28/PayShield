package com.payshield.payment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_events")
public class PaymentEventEntity {

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "from_status", length = 32)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 32)
    private String toStatus;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 200)
    private String actor;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentEventEntity() {}

    public PaymentEventEntity(UUID id, UUID paymentId, String fromStatus, String toStatus,
                              String eventType, String actor, String correlationId,
                              String detailsJson, Instant createdAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.eventType = eventType;
        this.actor = actor;
        this.correlationId = correlationId;
        this.detailsJson = detailsJson;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getEventType() { return eventType; }
    public String getActor() { return actor; }
    public String getCorrelationId() { return correlationId; }
    public String getDetailsJson() { return detailsJson; }
    public Instant getCreatedAt() { return createdAt; }
}
