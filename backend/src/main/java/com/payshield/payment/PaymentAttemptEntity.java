package com.payshield.payment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttemptEntity {

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "provider_code", nullable = false, length = 32)
    private String providerCode;

    @Column(name = "provider_request_id", nullable = false, unique = true, length = 128)
    private String providerRequestId;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(nullable = false, length = 32)
    private String outcome;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentAttemptEntity() {}

    public PaymentAttemptEntity(UUID id, UUID paymentId, int attemptNumber, String providerCode,
                                String providerRequestId, String providerReference, String outcome,
                                int latencyMs, String errorCode, String errorMessage, Instant createdAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.attemptNumber = attemptNumber;
        this.providerCode = providerCode;
        this.providerRequestId = providerRequestId;
        this.providerReference = providerReference;
        this.outcome = outcome;
        this.latencyMs = latencyMs;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public int getAttemptNumber() { return attemptNumber; }
    public String getProviderCode() { return providerCode; }
    public String getProviderRequestId() { return providerRequestId; }
    public String getProviderReference() { return providerReference; }
    public String getOutcome() { return outcome; }
    public int getLatencyMs() { return latencyMs; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
