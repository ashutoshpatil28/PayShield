package com.payshield.audit;

import com.payshield.common.CorrelationIdFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorUserId,
                       String actorEmail,
                       String actorRoles,
                       String action,
                       String resourceType,
                       String resourceId,
                       AuditOutcome outcome,
                       String ipAddress,
                       String detailsJson) {
        AuditEventEntity event = new AuditEventEntity(
                UUID.randomUUID(),
                actorUserId,
                actorEmail,
                actorRoles,
                action,
                resourceType,
                resourceId,
                outcome,
                CorrelationIdFilter.currentCorrelationId(),
                ipAddress,
                detailsJson,
                Instant.now()
        );
        repository.save(event);
    }

    @Transactional(readOnly = true)
    public Page<AuditEventDto> search(String action, String resourceType, AuditOutcome outcome, Pageable pageable) {
        String normAction = (action == null || action.isBlank()) ? null : action.trim();
        String normResource = (resourceType == null || resourceType.isBlank()) ? null : resourceType.trim();
        return repository.search(normAction, normResource, outcome, pageable)
                .map(AuditEventDto::from);
    }
}
