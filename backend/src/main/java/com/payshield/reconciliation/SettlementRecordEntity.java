package com.payshield.reconciliation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_records")
public class SettlementRecordEntity {

    @Id
    private UUID id;

    @Column(name = "settlement_file_id", nullable = false)
    private UUID settlementFileId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "provider_reference", nullable = false, length = 128)
    private String providerReference;

    @Column(name = "merchant_order_ref", length = 100)
    private String merchantOrderRef;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "fee_minor", nullable = false)
    private long feeMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "settlement_date", nullable = false, length = 32)
    private String settlementDate;

    @Column(name = "match_status", nullable = false, length = 32)
    private String matchStatus;

    @Column(name = "matched_payment_id")
    private UUID matchedPaymentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SettlementRecordEntity() {}

    public SettlementRecordEntity(UUID id, UUID settlementFileId, int lineNumber, String providerReference,
                                  String merchantOrderRef, long amountMinor, long feeMinor, String currency,
                                  String settlementDate, String matchStatus, UUID matchedPaymentId, Instant createdAt) {
        this.id = id;
        this.settlementFileId = settlementFileId;
        this.lineNumber = lineNumber;
        this.providerReference = providerReference;
        this.merchantOrderRef = merchantOrderRef;
        this.amountMinor = amountMinor;
        this.feeMinor = feeMinor;
        this.currency = currency;
        this.settlementDate = settlementDate;
        this.matchStatus = matchStatus;
        this.matchedPaymentId = matchedPaymentId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getSettlementFileId() { return settlementFileId; }
    public int getLineNumber() { return lineNumber; }
    public String getProviderReference() { return providerReference; }
    public String getMerchantOrderRef() { return merchantOrderRef; }
    public long getAmountMinor() { return amountMinor; }
    public long getFeeMinor() { return feeMinor; }
    public String getCurrency() { return currency; }
    public String getSettlementDate() { return settlementDate; }
    public String getMatchStatus() { return matchStatus; }
    public UUID getMatchedPaymentId() { return matchedPaymentId; }
    public Instant getCreatedAt() { return createdAt; }
}
