package com.payshield.outbox;

import com.payshield.common.CorrelationIdFilter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxService {

    public record PublishedOutboxEvent(
            UUID eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            String payloadJson,
            String correlationId
    ) {}

    private final OutboxEventRepository outboxRepository;
    private final InboxEventRepository inboxRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OutboxService(OutboxEventRepository outboxRepository,
                         InboxEventRepository inboxRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.outboxRepository = outboxRepository;
        this.inboxRepository = inboxRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEventEntity enqueue(String eventType, String aggregateType, String aggregateId, String payloadJson) {
        OutboxEventEntity entity = new OutboxEventEntity(
                UUID.randomUUID(),
                eventType,
                aggregateType,
                aggregateId,
                payloadJson,
                CorrelationIdFilter.currentCorrelationId(),
                Instant.now()
        );
        return outboxRepository.save(entity);
    }

    /**
     * Attempts to claim an event key for a specific consumer (inbox pattern).
     * Returns true if newly claimed, false if already processed (duplicate delivery).
     */
    @Transactional
    public boolean tryClaimInbox(String consumerName, String eventKey) {
        if (inboxRepository.existsByConsumerNameAndEventKey(consumerName, eventKey)) {
            return false;
        }
        try {
            inboxRepository.saveAndFlush(new InboxEventEntity(
                    UUID.randomUUID(),
                    consumerName,
                    eventKey,
                    CorrelationIdFilter.currentCorrelationId(),
                    Instant.now()
            ));
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public int relayPendingEvents() {
        List<OutboxEventEntity> pending = outboxRepository.findByPublishStatusOrderByCreatedAtAsc(
                OutboxEventEntity.PublishStatus.PENDING, PageRequest.of(0, 50));
        int published = 0;
        for (OutboxEventEntity event : pending) {
            try {
                eventPublisher.publishEvent(new PublishedOutboxEvent(
                        event.getId(),
                        event.getEventType(),
                        event.getAggregateType(),
                        event.getAggregateId(),
                        event.getPayloadJson(),
                        event.getCorrelationId()
                ));
                event.markPublished();
                published++;
            } catch (Exception ex) {
                event.recordFailure(ex.getMessage());
            }
            outboxRepository.save(event);
        }
        return published;
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return outboxRepository.countByPublishStatus(OutboxEventEntity.PublishStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public long countFailed() {
        return outboxRepository.countByPublishStatus(OutboxEventEntity.PublishStatus.FAILED);
    }
}
