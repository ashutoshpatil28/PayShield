package com.payshield.payment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "merchant_order_ref", nullable = false, length = 100)
    private String merchantOrderRef;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "refunded_amount_minor", nullable = false)
    private long refundedAmountMinor;

    @Column(name = "selected_provider", nullable = false, length = 32)
    private String selectedProvider;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "is_demo_data", nullable = false)
    private boolean demoData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentEntity() {}

    public PaymentEntity(UUID id, UUID merchantId, String merchantOrderRef, long amountMinor,
                         String currency, String selectedProvider, String idempotencyKey,
                         boolean demoData, Instant createdAt) {
        this.id = id;
        this.merchantId = merchantId;
        this.merchantOrderRef = merchantOrderRef;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = PaymentStatus.CREATED;
        this.refundedAmountMinor = 0L;
        this.selectedProvider = selectedProvider;
        this.idempotencyKey = idempotencyKey;
        this.demoData = demoData;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getMerchantOrderRef() { return merchantOrderRef; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public long getRefundedAmountMinor() { return refundedAmountMinor; }
    public String getSelectedProvider() { return selectedProvider; }
    public String getProviderReference() { return providerReference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getFailureReason() { return failureReason; }
    public boolean isDemoData() { return demoData; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void transitionTo(PaymentStatus nextStatus, String providerReference, String failureReason) {
        PaymentStateMachine.assertTransition(this.status, nextStatus);
        this.status = nextStatus;
        if (providerReference != null) {
            this.providerReference = providerReference;
        }
        if (failureReason != null) {
            this.failureReason = failureReason;
        }
        this.updatedAt = Instant.now();
    }
}
