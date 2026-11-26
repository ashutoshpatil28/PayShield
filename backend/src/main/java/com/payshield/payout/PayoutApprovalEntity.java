package com.payshield.payout;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_approvals")
public class PayoutApprovalEntity {

    @Id
    private UUID id;

    @Column(name = "payout_request_id", nullable = false, unique = true)
    private UUID payoutRequestId;

    @Column(name = "maker_user_id", nullable = false)
    private UUID makerUserId;

    @Column(name = "checker_user_id", nullable = false)
    private UUID checkerUserId;

    @Column(nullable = false, length = 24)
    private String decision;

    @Column(length = 500)
    private String comments;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    protected PayoutApprovalEntity() {}

    public PayoutApprovalEntity(UUID id, UUID payoutRequestId, UUID makerUserId, UUID checkerUserId,
                                String decision, String comments, Instant decidedAt) {
        this.id = id;
        this.payoutRequestId = payoutRequestId;
        this.makerUserId = makerUserId;
        this.checkerUserId = checkerUserId;
        this.decision = decision;
        this.comments = comments;
        this.decidedAt = decidedAt;
    }

    public UUID getId() { return id; }
    public UUID getPayoutRequestId() { return payoutRequestId; }
    public UUID getMakerUserId() { return makerUserId; }
    public UUID getCheckerUserId() { return checkerUserId; }
    public String getDecision() { return decision; }
    public String getComments() { return comments; }
    public Instant getDecidedAt() { return decidedAt; }
}
