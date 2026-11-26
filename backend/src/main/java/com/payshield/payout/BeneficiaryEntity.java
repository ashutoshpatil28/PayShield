package com.payshield.payout;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "beneficiaries")
public class BeneficiaryEntity {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "beneficiary_name", nullable = false, length = 150)
    private String beneficiaryName;

    @Column(name = "account_number_masked", nullable = false, length = 32)
    private String accountNumberMasked;

    @Column(name = "ifsc_or_routing", nullable = false, length = 32)
    private String ifscOrRouting;

    @Column(nullable = false)
    private boolean allowlisted;

    @Column(name = "is_demo_data", nullable = false)
    private boolean demoData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BeneficiaryEntity() {}

    public BeneficiaryEntity(UUID id, UUID merchantId, String beneficiaryName, String accountNumberMasked,
                             String ifscOrRouting, boolean allowlisted, boolean demoData, Instant createdAt) {
        this.id = id;
        this.merchantId = merchantId;
        this.beneficiaryName = beneficiaryName;
        this.accountNumberMasked = accountNumberMasked;
        this.ifscOrRouting = ifscOrRouting;
        this.allowlisted = allowlisted;
        this.demoData = demoData;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getBeneficiaryName() { return beneficiaryName; }
    public String getAccountNumberMasked() { return accountNumberMasked; }
    public String getIfscOrRouting() { return ifscOrRouting; }
    public boolean isAllowlisted() { return allowlisted; }
    public boolean isDemoData() { return demoData; }
    public Instant getCreatedAt() { return createdAt; }

    public void setAllowlisted(boolean allowlisted) {
        this.allowlisted = allowlisted;
    }
}
