package com.payshield.risk;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RiskAlertRepository extends JpaRepository<RiskAlertEntity, UUID> {
    Page<RiskAlertEntity> findByStatusOrderByCreatedAtDesc(RiskAlertEntity.AlertStatus status, Pageable pageable);
    Page<RiskAlertEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByStatus(RiskAlertEntity.AlertStatus status);
}
