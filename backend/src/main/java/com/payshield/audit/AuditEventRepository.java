package com.payshield.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {

    @Query("""
            SELECT a FROM AuditEventEntity a
            WHERE (:action IS NULL OR a.action = :action)
              AND (:resourceType IS NULL OR a.resourceType = :resourceType)
              AND (:outcome IS NULL OR a.outcome = :outcome)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditEventEntity> search(
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("outcome") AuditOutcome outcome,
            Pageable pageable
    );
}
