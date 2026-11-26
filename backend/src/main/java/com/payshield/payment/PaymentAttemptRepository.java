package com.payshield.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, UUID> {
    List<PaymentAttemptEntity> findByPaymentIdOrderByAttemptNumberAsc(UUID paymentId);
}
