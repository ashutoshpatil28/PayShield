package com.payshield.payout;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutKillSwitchRepository extends JpaRepository<PayoutKillSwitchEntity, String> {
}
