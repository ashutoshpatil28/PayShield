package com.payshield.payout;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_requests")
public class PayoutRequestEntity {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "beneficiary_id", nullable = false)
    private UUID beneficiaryId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PayoutStatus status;

    @Column(nullable = false, length = 255)
    private String purpose;

    @Column(name = "maker_user_id", nullable = false)
    private UUID makerUserId;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "approval_expires_at")
    private Instant approvalExpiresAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "is_demo_data", nullable = false)
    private boolean demoData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PayoutRequestEntity() {}

    public PayoutRequestEntity(UUID id, UUID merchantId, UUID beneficiaryId, long amountMinor,
                               String currency, String purpose, UUID makerUserId,
                               boolean demoData, Instant createdAt) {
        this.id = id;
        this.merchantId = merchantId;
        this.beneficiaryId = beneficiaryId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = PayoutStatus.REQUESTED;
        this.purpose = purpose;
        this.makerUserId = makerUserId;
        this.demoData = demoData;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public UUID getBeneficiaryId() { return beneficiaryId; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public PayoutStatus getStatus() { return status; }
    public String getPurpose() { return purpose; }
    public UUID getMakerUserId() { return makerUserId; }
    public int getRiskScore() { return riskScore; }
    public Instant getApprovalExpiresAt() { return approvalExpiresAt; }
    public String getFailureReason() { return failureReason; }
    public boolean isDemoData() { return demoData; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void transitionTo(PayoutStatus nextStatus, String failureReason) {
        PayoutStateMachine.assertTransition(this.status, nextStatus);
        this.status = nextStatus;
        if (failureReason != null) {
            this.failureReason = failureReason;
        }
        this.updatedAt = Instant.now();
    }

    public void setApprovalExpiresAt(Instant approvalExpiresAt) {
        this.approvalExpiresAt = approvalExpiresAt;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }
}
