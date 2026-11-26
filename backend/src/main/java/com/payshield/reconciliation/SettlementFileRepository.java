package com.payshield.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SettlementFileRepository extends JpaRepository<SettlementFileEntity, UUID> {
    boolean existsByFileSha256(String fileSha256);
    List<SettlementFileEntity> findAllByOrderByCreatedAtDesc();
}
