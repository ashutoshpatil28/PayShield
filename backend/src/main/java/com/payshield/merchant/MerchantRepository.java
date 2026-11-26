package com.payshield.merchant;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<MerchantEntity, UUID> {
    Optional<MerchantEntity> findByMerchantCode(String merchantCode);
    boolean existsByMerchantCode(String merchantCode);
}
