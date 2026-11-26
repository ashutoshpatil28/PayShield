package com.payshield.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEventDto(
        UUID id,
        UUID actorUserId,
        String actorEmail,
        String actorRoles,
        String action,
        String resourceType,
        String resourceId,
        AuditOutcome outcome,
        String correlationId,
        String ipAddress,
        String detailsJson,
        Instant createdAt
) {
    public static AuditEventDto from(AuditEventEntity e) {
        return new AuditEventDto(
                e.getId(),
                e.getActorUserId(),
                e.getActorEmail(),
                e.getActorRoles(),
                e.getAction(),
                e.getResourceType(),
                e.getResourceId(),
                e.getOutcome(),
                e.getCorrelationId(),
                e.getIpAddress(),
                e.getDetailsJson(),
                e.getCreatedAt()
        );
    }
}
