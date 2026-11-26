package com.payshield.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface InboxEventRepository extends JpaRepository<InboxEventEntity, UUID> {
    boolean existsByConsumerNameAndEventKey(String consumerName, String eventKey);
}
