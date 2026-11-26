package com.payshield.payout;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payout_kill_switch")
public class PayoutKillSwitchEntity {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 255)
    private String reason;

    @Column(name = "updated_by", nullable = false, length = 200)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PayoutKillSwitchEntity() {}

    public String getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public String getReason() { return reason; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(boolean enabled, String reason, String updatedBy) {
        this.enabled = enabled;
        this.reason = reason;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }
}
