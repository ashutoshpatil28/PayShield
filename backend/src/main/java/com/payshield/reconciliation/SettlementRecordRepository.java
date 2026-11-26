package com.payshield.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SettlementRecordRepository extends JpaRepository<SettlementRecordEntity, UUID> {
    List<SettlementRecordEntity> findBySettlementFileIdOrderByLineNumberAsc(UUID settlementFileId);
}
