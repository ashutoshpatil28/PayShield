package com.payshield.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    public enum PublishStatus { PENDING, PUBLISHED, FAILED }

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 128)
    private String aggregateId;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_status", nullable = false, length = 24)
    private PublishStatus publishStatus;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEventEntity() {}

    public OutboxEventEntity(UUID id, String eventType, String aggregateType, String aggregateId,
                             String payloadJson, String correlationId, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payloadJson = payloadJson;
        this.publishStatus = PublishStatus.PENDING;
        this.retryCount = 0;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getPayloadJson() { return payloadJson; }
    public PublishStatus getPublishStatus() { return publishStatus; }
    public int getRetryCount() { return retryCount; }
    public String getLastError() { return lastError; }
    public String getCorrelationId() { return correlationId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }

    public void markPublished() {
        this.publishStatus = PublishStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.retryCount++;
        this.lastError = error != null && error.length() > 490 ? error.substring(0, 490) : error;
        if (this.retryCount >= 5) {
            this.publishStatus = PublishStatus.FAILED;
        }
    }
}
