package com.payshield.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JournalLineRepository extends JpaRepository<JournalLineEntity, UUID> {
    List<JournalLineEntity> findByJournalTxId(UUID journalTxId);
}
