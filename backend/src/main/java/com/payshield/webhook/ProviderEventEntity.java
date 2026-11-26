package com.payshield.webhook;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_events")
public class ProviderEventEntity {

    public enum ProcessingStatus { PROCESSED, DUPLICATE, REJECTED, FAILED }

    @Id
    private UUID id;

    @Column(name = "provider_code", nullable = false, length = 32)
    private String providerCode;

    @Column(name = "provider_event_id", nullable = false, length = 128)
    private String providerEventId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 24)
    private ProcessingStatus processingStatus;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected ProviderEventEntity() {}

    public ProviderEventEntity(UUID id, String providerCode, String providerEventId, UUID paymentId,
                               String eventType, String payloadJson, boolean signatureValid,
                               ProcessingStatus processingStatus, String errorMessage,
                               Instant receivedAt, Instant processedAt) {
        this.id = id;
        this.providerCode = providerCode;
        this.providerEventId = providerEventId;
        this.paymentId = paymentId;
        this.eventType = eventType;
        this.payloadJson = payloadJson;
        this.signatureValid = signatureValid;
        this.processingStatus = processingStatus;
        this.errorMessage = errorMessage;
        this.receivedAt = receivedAt;
        this.processedAt = processedAt;
    }

    public UUID getId() { return id; }
    public String getProviderCode() { return providerCode; }
    public String getProviderEventId() { return providerEventId; }
    public UUID getPaymentId() { return paymentId; }
    public String getEventType() { return eventType; }
    public String getPayloadJson() { return payloadJson; }
    public boolean isSignatureValid() { return signatureValid; }
    public ProcessingStatus getProcessingStatus() { return processingStatus; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
}
