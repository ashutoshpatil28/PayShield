package com.payshield.reconciliation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_cases")
public class ReconciliationCaseEntity {

    public enum CaseStatus { OPEN, INVESTIGATING, RESOLVED, DISMISSED }

    @Id
    private UUID id;

    @Column(name = "settlement_file_id", nullable = false)
    private UUID settlementFileId;

    @Column(name = "settlement_record_id")
    private UUID settlementRecordId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_category", nullable = false, length = 48)
    private ExceptionCategory exceptionCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CaseStatus status;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "resolved_by", length = 200)
    private String resolvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected ReconciliationCaseEntity() {}

    public ReconciliationCaseEntity(UUID id, UUID settlementFileId, UUID settlementRecordId, UUID paymentId,
                                    ExceptionCategory exceptionCategory, String summary, Instant createdAt) {
        this.id = id;
        this.settlementFileId = settlementFileId;
        this.settlementRecordId = settlementRecordId;
        this.paymentId = paymentId;
        this.exceptionCategory = exceptionCategory;
        this.status = CaseStatus.OPEN;
        this.summary = summary;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getSettlementFileId() { return settlementFileId; }
    public UUID getSettlementRecordId() { return settlementRecordId; }
    public UUID getPaymentId() { return paymentId; }
    public ExceptionCategory getExceptionCategory() { return exceptionCategory; }
    public CaseStatus getStatus() { return status; }
    public String getSummary() { return summary; }
    public String getResolutionNotes() { return resolutionNotes; }
    public String getResolvedBy() { return resolvedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }

    public void updateStatus(CaseStatus nextStatus, String notes, String actor) {
        this.status = nextStatus;
        this.resolutionNotes = notes;
        if (nextStatus == CaseStatus.RESOLVED || nextStatus == CaseStatus.DISMISSED) {
            this.resolvedBy = actor;
            this.resolvedAt = Instant.now();
        }
    }
}
