package com.payshield.payout;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PayoutApprovalRepository extends JpaRepository<PayoutApprovalEntity, UUID> {
    Optional<PayoutApprovalEntity> findByPayoutRequestId(UUID payoutRequestId);
}
