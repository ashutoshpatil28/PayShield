package com.payshield.reconciliation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationCaseRepository extends JpaRepository<ReconciliationCaseEntity, UUID> {
    Page<ReconciliationCaseEntity> findByStatusOrderByCreatedAtDesc(ReconciliationCaseEntity.CaseStatus status, Pageable pageable);
    Page<ReconciliationCaseEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByStatus(ReconciliationCaseEntity.CaseStatus status);
}
