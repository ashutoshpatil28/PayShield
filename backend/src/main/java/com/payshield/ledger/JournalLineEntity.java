package com.payshield.ledger;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journal_lines")
public class JournalLineEntity {

    public enum EntryType { DEBIT, CREDIT }

    @Id
    private UUID id;

    @Column(name = "journal_tx_id", nullable = false)
    private UUID journalTxId;

    @Column(name = "ledger_account_id", nullable = false)
    private UUID ledgerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 12)
    private EntryType entryType;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected JournalLineEntity() {}

    public JournalLineEntity(UUID id, UUID journalTxId, UUID ledgerAccountId, EntryType entryType,
                             long amountMinor, String currency, Instant createdAt) {
        this.id = id;
        this.journalTxId = journalTxId;
        this.ledgerAccountId = ledgerAccountId;
        this.entryType = entryType;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getJournalTxId() { return journalTxId; }
    public UUID getLedgerAccountId() { return ledgerAccountId; }
    public EntryType getEntryType() { return entryType; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
}
