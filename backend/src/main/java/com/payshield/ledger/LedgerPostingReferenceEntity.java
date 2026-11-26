package com.payshield.ledger;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_posting_references")
public class LedgerPostingReferenceEntity {

    @Id
    private UUID id;

    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 128)
    private String sourceId;

    @Column(name = "posting_type", nullable = false, length = 40)
    private String postingType;

    @Column(name = "journal_tx_id", nullable = false)
    private UUID journalTxId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerPostingReferenceEntity() {}

    public LedgerPostingReferenceEntity(UUID id, String sourceType, String sourceId,
                                        String postingType, UUID journalTxId, Instant createdAt) {
        this.id = id;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.postingType = postingType;
        this.journalTxId = journalTxId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getSourceType() { return sourceType; }
    public String getSourceId() { return sourceId; }
    public String getPostingType() { return postingType; }
    public UUID getJournalTxId() { return journalTxId; }
    public Instant getCreatedAt() { return createdAt; }
}
