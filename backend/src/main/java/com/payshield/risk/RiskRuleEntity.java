package com.payshield.risk;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "risk_rules")
public class RiskRuleEntity {

    @Id
    @Column(name = "rule_code", length = 64)
    private String ruleCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, length = 24)
    private String severity;

    @Column(name = "score_weight", nullable = false)
    private int scoreWeight;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "threshold_value", nullable = false)
    private long thresholdValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RiskRuleEntity() {}

    public String getRuleCode() { return ruleCode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSeverity() { return severity; }
    public int getScoreWeight() { return scoreWeight; }
    public boolean isEnabled() { return enabled; }
    public long getThresholdValue() { return thresholdValue; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(boolean enabled, long thresholdValue, int scoreWeight) {
        this.enabled = enabled;
        this.thresholdValue = thresholdValue;
        this.scoreWeight = scoreWeight;
        this.updatedAt = Instant.now();
    }
}
