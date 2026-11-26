package com.payshield.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByProviderReference(String providerReference);

    @Query("""
            SELECT p FROM PaymentEntity p
            WHERE (:merchantId IS NULL OR p.merchantId = :merchantId)
              AND (:status IS NULL OR p.status = :status)
              AND (:provider IS NULL OR p.selectedProvider = :provider)
              AND (:search IS NULL OR LOWER(p.merchantOrderRef) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(p.providerReference, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<PaymentEntity> search(
            @Param("merchantId") UUID merchantId,
            @Param("status") PaymentStatus status,
            @Param("provider") String provider,
            @Param("search") String search,
            Pageable pageable
    );

    long countByStatus(PaymentStatus status);
}
