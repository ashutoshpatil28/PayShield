package com.payshield.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccountEntity, UUID> {
    Optional<LedgerAccountEntity> findByAccountCode(String accountCode);
}
