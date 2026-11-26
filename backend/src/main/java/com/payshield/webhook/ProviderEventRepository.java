package com.payshield.webhook;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProviderEventRepository extends JpaRepository<ProviderEventEntity, UUID> {
    boolean existsByProviderCodeAndProviderEventId(String providerCode, String providerEventId);
    Page<ProviderEventEntity> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
