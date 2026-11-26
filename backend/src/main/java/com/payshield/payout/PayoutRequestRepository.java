package com.payshield.payout;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequestEntity, UUID> {

    @Query("""
            SELECT COALESCE(SUM(p.amountMinor), 0) FROM PayoutRequestEntity p
            WHERE p.merchantId = :merchantId
              AND p.createdAt >= :since
              AND p.status <> com.payshield.payout.PayoutStatus.REJECTED
              AND p.status <> com.payshield.payout.PayoutStatus.FAILED
            """)
    long sumMerchantDailyVolume(@Param("merchantId") UUID merchantId, @Param("since") Instant since);

    @Query("""
            SELECT p FROM PayoutRequestEntity p
            WHERE (:merchantId IS NULL OR p.merchantId = :merchantId)
              AND (:status IS NULL OR p.status = :status)
            ORDER BY p.createdAt DESC
            """)
    Page<PayoutRequestEntity> search(
            @Param("merchantId") UUID merchantId,
            @Param("status") PayoutStatus status,
            Pageable pageable
    );

    long countByStatus(PayoutStatus status);
}
