package com.payshield.provider;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "provider_configs")
public class ProviderConfigEntity {

    @Id
    @Column(name = "provider_code", length = 32)
    private String providerCode;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_mode", nullable = false, length = 40)
    private SimulatorFailureMode failureMode;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "failure_rate_percent", nullable = false)
    private int failureRatePercent;

    @Column(name = "timeout_rate_percent", nullable = false)
    private int timeoutRatePercent;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProviderConfigEntity() {}

    public String getProviderCode() { return providerCode; }
    public String getDisplayName() { return displayName; }
    public boolean isEnabled() { return enabled; }
    public SimulatorFailureMode getFailureMode() { return failureMode; }
    public int getLatencyMs() { return latencyMs; }
    public int getFailureRatePercent() { return failureRatePercent; }
    public int getTimeoutRatePercent() { return timeoutRatePercent; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateConfig(boolean enabled, SimulatorFailureMode failureMode,
                             int latencyMs, int failureRatePercent, int timeoutRatePercent) {
        this.enabled = enabled;
        this.failureMode = failureMode;
        this.latencyMs = Math.max(0, latencyMs);
        this.failureRatePercent = Math.min(100, Math.max(0, failureRatePercent));
        this.timeoutRatePercent = Math.min(100, Math.max(0, timeoutRatePercent));
        this.updatedAt = Instant.now();
    }
}
