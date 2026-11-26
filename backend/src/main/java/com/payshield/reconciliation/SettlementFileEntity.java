package com.payshield.reconciliation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_files")
public class SettlementFileEntity {

    @Id
    private UUID id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_sha256", nullable = false, unique = true, length = 64)
    private String fileSha256;

    @Column(name = "provider_code", nullable = false, length = 32)
    private String providerCode;

    @Column(name = "total_records", nullable = false)
    private int totalRecords;

    @Column(name = "matched_records", nullable = false)
    private int matchedRecords;

    @Column(name = "unmatched_records", nullable = false)
    private int unmatchedRecords;

    @Column(name = "uploaded_by", nullable = false, length = 200)
    private String uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SettlementFileEntity() {}

    public SettlementFileEntity(UUID id, String fileName, String fileSha256, String providerCode,
                                int totalRecords, int matchedRecords, int unmatchedRecords,
                                String uploadedBy, Instant createdAt) {
        this.id = id;
        this.fileName = fileName;
        this.fileSha256 = fileSha256;
        this.providerCode = providerCode;
        this.totalRecords = totalRecords;
        this.matchedRecords = matchedRecords;
        this.unmatchedRecords = unmatchedRecords;
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getFileName() { return fileName; }
    public String getFileSha256() { return fileSha256; }
    public String getProviderCode() { return providerCode; }
    public int getTotalRecords() { return totalRecords; }
    public int getMatchedRecords() { return matchedRecords; }
    public int getUnmatchedRecords() { return unmatchedRecords; }
    public String getUploadedBy() { return uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
