package com.payshield.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity> findByPublishStatusOrderByCreatedAtAsc(OutboxEventEntity.PublishStatus status, Pageable pageable);
    long countByPublishStatus(OutboxEventEntity.PublishStatus status);
}
