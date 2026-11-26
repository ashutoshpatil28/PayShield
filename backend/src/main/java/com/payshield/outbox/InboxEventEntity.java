package com.payshield.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox_events")
public class InboxEventEntity {

    @Id
    private UUID id;

    @Column(name = "consumer_name", nullable = false, length = 80)
    private String consumerName;

    @Column(name = "event_key", nullable = false, length = 200)
    private String eventKey;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected InboxEventEntity() {}

    public InboxEventEntity(UUID id, String consumerName, String eventKey, String correlationId, Instant processedAt) {
        this.id = id;
        this.consumerName = consumerName;
        this.eventKey = eventKey;
        this.correlationId = correlationId;
        this.processedAt = processedAt;
    }

    public UUID getId() { return id; }
    public String getConsumerName() { return consumerName; }
    public String getEventKey() { return eventKey; }
    public String getCorrelationId() { return correlationId; }
    public Instant getProcessedAt() { return processedAt; }
}
