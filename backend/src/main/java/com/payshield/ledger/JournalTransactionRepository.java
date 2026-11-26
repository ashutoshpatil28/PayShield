package com.payshield.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JournalTransactionRepository extends JpaRepository<JournalTransactionEntity, UUID> {
    Page<JournalTransactionEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
