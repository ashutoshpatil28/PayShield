package com.payshield.ledger;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_balances")
public class LedgerBalanceEntity {

    @Id
    @Column(name = "ledger_account_id")
    private UUID ledgerAccountId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerBalanceEntity() {}

    public LedgerBalanceEntity(UUID ledgerAccountId, String currency, long balanceMinor, Instant updatedAt) {
        this.ledgerAccountId = ledgerAccountId;
        this.currency = currency;
        this.balanceMinor = balanceMinor;
        this.updatedAt = updatedAt;
    }

    public UUID getLedgerAccountId() { return ledgerAccountId; }
    public String getCurrency() { return currency; }
    public long getBalanceMinor() { return balanceMinor; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void applyDelta(long deltaMinor) {
        this.balanceMinor = Math.addExact(this.balanceMinor, deltaMinor);
        this.updatedAt = Instant.now();
    }
}
