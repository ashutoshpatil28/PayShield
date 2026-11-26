package com.payshield.ledger;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journal_transactions")
public class JournalTransactionEntity {

    @Id
    private UUID id;

    @Column(name = "reference_code", nullable = false, unique = true, length = 100)
    private String referenceCode;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "reversal_of_tx_id")
    private UUID reversalOfTxId;

    @Column(name = "created_by", nullable = false, length = 200)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected JournalTransactionEntity() {}

    public JournalTransactionEntity(UUID id, String referenceCode, String description, String currency,
                                    UUID reversalOfTxId, String createdBy, Instant createdAt) {
        this.id = id;
        this.referenceCode = referenceCode;
        this.description = description;
        this.currency = currency;
        this.reversalOfTxId = reversalOfTxId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getReferenceCode() { return referenceCode; }
    public String getDescription() { return description; }
    public String getCurrency() { return currency; }
    public UUID getReversalOfTxId() { return reversalOfTxId; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
