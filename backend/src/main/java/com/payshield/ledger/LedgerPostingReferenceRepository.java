package com.payshield.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface LedgerPostingReferenceRepository extends JpaRepository<LedgerPostingReferenceEntity, UUID> {
    Optional<LedgerPostingReferenceEntity> findBySourceTypeAndSourceIdAndPostingType(
            String sourceType, String sourceId, String postingType);
}
