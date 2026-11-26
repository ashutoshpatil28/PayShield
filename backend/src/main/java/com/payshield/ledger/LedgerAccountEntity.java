package com.payshield.ledger;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_accounts")
public class LedgerAccountEntity {

    public enum AccountType { ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE }

    @Id
    private UUID id;

    @Column(name = "account_code", nullable = false, unique = true, length = 80)
    private String accountCode;

    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 24)
    private AccountType accountType;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerAccountEntity() {}

    public LedgerAccountEntity(UUID id, String accountCode, String accountName, AccountType accountType,
                               UUID merchantId, String currency, Instant createdAt) {
        this.id = id;
        this.accountCode = accountCode;
        this.accountName = accountName;
        this.accountType = accountType;
        this.merchantId = merchantId;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getAccountCode() { return accountCode; }
    public String getAccountName() { return accountName; }
    public AccountType getAccountType() { return accountType; }
    public UUID getMerchantId() { return merchantId; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
}
