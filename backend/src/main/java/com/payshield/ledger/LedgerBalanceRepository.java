package com.payshield.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LedgerBalanceRepository extends JpaRepository<LedgerBalanceEntity, UUID> {
}
