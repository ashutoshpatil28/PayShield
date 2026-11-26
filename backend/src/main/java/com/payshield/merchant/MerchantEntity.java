package com.payshield.merchant;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class MerchantEntity {

    @Id
    private UUID id;

    @Column(name = "merchant_code", nullable = false, unique = true, length = 64)
    private String merchantCode;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MerchantStatus status;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency;

    @Column(name = "is_demo_data", nullable = false)
    private boolean demoData = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MerchantEntity() {
    }

    public MerchantEntity(UUID id, String merchantCode, String legalName, String displayName,
                          MerchantStatus status, String defaultCurrency, boolean demoData,
                          Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.merchantCode = merchantCode;
        this.legalName = legalName;
        this.displayName = displayName;
        this.status = status;
        this.defaultCurrency = defaultCurrency;
        this.demoData = demoData;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getMerchantCode() { return merchantCode; }
    public String getLegalName() { return legalName; }
    public String getDisplayName() { return displayName; }
    public MerchantStatus getStatus() { return status; }
    public String getDefaultCurrency() { return defaultCurrency; }
    public boolean isDemoData() { return demoData; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(MerchantStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
