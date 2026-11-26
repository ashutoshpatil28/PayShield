package com.payshield.payment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecordEntity {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecordEntity() {}

    public IdempotencyRecordEntity(UUID id, UUID merchantId, String idempotencyKey,
                                   String requestHash, Instant createdAt) {
        this.id = id;
        this.merchantId = merchantId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = "IN_PROGRESS";
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public UUID getPaymentId() { return paymentId; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void markCompleted(UUID paymentId) {
        this.paymentId = paymentId;
        this.status = "COMPLETED";
    }
}
