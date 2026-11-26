package com.payshield.reconciliation;

import com.payshield.audit.AuditOutcome;
import com.payshield.audit.AuditService;
import com.payshield.common.DomainException;
import com.payshield.common.PagedResponse;
import com.payshield.payment.PaymentEntity;
import com.payshield.payment.PaymentRepository;
import com.payshield.payment.PaymentStatus;
import com.payshield.provider.ProviderCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
public class ReconciliationService {

    private final SettlementFileRepository fileRepository;
    private final SettlementRecordRepository recordRepository;
    private final ReconciliationCaseRepository caseRepository;
    private final PaymentRepository paymentRepository;
    private final AuditService auditService;

    public ReconciliationService(
            SettlementFileRepository fileRepository,
            SettlementRecordRepository recordRepository,
            ReconciliationCaseRepository caseRepository,
            PaymentRepository paymentRepository,
            AuditService auditService
    ) {
        this.fileRepository = fileRepository;
        this.recordRepository = recordRepository;
        this.caseRepository = caseRepository;
        this.paymentRepository = paymentRepository;
        this.auditService = auditService;
    }

    public record ReconciliationSummaryDto(
            long totalFiles,
            long totalImportedRecords,
            long matchedRecords,
            long unmatchedRecords,
            long openCases,
            long resolvedCases,
            double completionRatePercent
    ) {}

    public record UploadResultDto(
            SettlementFileEntity file,
            List<SettlementRecordEntity> records,
            int casesCreated
    ) {}

    @Transactional
    public UploadResultDto ingestCsv(String fileName, ProviderCode providerCode, String csvContent,
                                     UUID actorId, String actorEmail, String roles, String ip) {
        if (csvContent == null || csvContent.isBlank()) {
            throw new DomainException("EMPTY_SETTLEMENT_FILE", "Settlement CSV content is empty");
        }
        String sha256 = sha256Hex(csvContent);
        if (fileRepository.existsByFileSha256(sha256)) {
            throw new DomainException("DUPLICATE_SETTLEMENT_FILE",
                    "This settlement file has already been ingested (SHA-256 match)", HttpStatus.CONFLICT);
        }

        String[] lines = csvContent.replace("\r\n", "\n").split("\n");
        if (lines.length < 2) {
            throw new DomainException("INVALID_CSV_STRUCTURE",
                    "CSV must contain header and at least one settlement record");
        }

        String header = lines[0].trim().toLowerCase();
        if (!header.startsWith("provider_reference,merchant_order_ref,amount_minor,fee_minor,currency,settlement_date")) {
            throw new DomainException("INVALID_CSV_HEADER",
                    "Expected CSV header: provider_reference,merchant_order_ref,amount_minor,fee_minor,currency,settlement_date");
        }

        UUID fileId = UUID.randomUUID();
        Instant now = Instant.now();
        int total = 0;
        int matched = 0;
        int unmatched = 0;
        int casesCreated = 0;

        Set<String> seenProviderRefsInFile = new HashSet<>();
        List<SettlementRecordEntity> savedRecords = new ArrayList<>();

        // Save file header first (updated after processing)
        SettlementFileEntity fileEntity = new SettlementFileEntity(
                fileId, fileName, sha256, providerCode.name(), 0, 0, 0, actorEmail, now
        );
        fileRepository.save(fileEntity);

        for (int i = 1; i < lines.length; i++) {
            String rawLine = lines[i].trim();
            if (rawLine.isEmpty()) continue;
            total++;

            String[] cols = rawLine.split(",");
            if (cols.length < 6) {
                throw new DomainException("INVALID_CSV_ROW",
                        "Line " + (i + 1) + " has fewer than 6 columns");
            }
            String providerRef = cols[0].trim();
            String orderRef = cols[1].trim();
            long amountMinor;
            long feeMinor;
            try {
                amountMinor = Long.parseLong(cols[2].trim());
                feeMinor = Long.parseLong(cols[3].trim());
            } catch (NumberFormatException nfe) {
                throw new DomainException("INVALID_CSV_AMOUNT",
                        "Line " + (i + 1) + " has non-integer amount_minor or fee_minor");
            }
            String currency = cols[4].trim().toUpperCase();
            String settlementDate = cols[5].trim();

            UUID recordId = UUID.randomUUID();
            String matchStatus = "MATCHED";
            UUID matchedPaymentId = null;

            if (!seenProviderRefsInFile.add(providerRef)) {
                matchStatus = "DISCREPANCY";
                unmatched++;
                recordRepository.save(new SettlementRecordEntity(
                        recordId, fileId, i + 1, providerRef, orderRef, amountMinor, feeMinor,
                        currency, settlementDate, matchStatus, null, now));
                caseRepository.save(new ReconciliationCaseEntity(
                        UUID.randomUUID(), fileId, recordId, null, ExceptionCategory.DUPLICATE_TRANSACTION,
                        "Duplicate provider reference " + providerRef + " in settlement file line " + (i + 1), now));
                casesCreated++;
                continue;
            }

            Optional<PaymentEntity> paymentOpt = paymentRepository.findByProviderReference(providerRef);
            if (paymentOpt.isEmpty()) {
                matchStatus = "UNMATCHED";
                unmatched++;
                SettlementRecordEntity rec = recordRepository.save(new SettlementRecordEntity(
                        recordId, fileId, i + 1, providerRef, orderRef, amountMinor, feeMinor,
                        currency, settlementDate, matchStatus, null, now));
                savedRecords.add(rec);
                caseRepository.save(new ReconciliationCaseEntity(
                        UUID.randomUUID(), fileId, recordId, null, ExceptionCategory.UNKNOWN_PROVIDER_REFERENCE,
                        "No internal payment found for providerReference " + providerRef, now));
                casesCreated++;
                continue;
            }

            PaymentEntity payment = paymentOpt.get();
            matchedPaymentId = payment.getId();
            long expectedFee = Math.max(1L, (payment.getAmountMinor() * 2L) / 100L);

            ExceptionCategory discrepancy = null;
            String summary = null;
            if (!payment.getCurrency().equalsIgnoreCase(currency)) {
                discrepancy = ExceptionCategory.CURRENCY_MISMATCH;
                summary = "Currency mismatch: internal=" + payment.getCurrency() + ", settlement=" + currency;
            } else if (payment.getAmountMinor() != amountMinor) {
                discrepancy = ExceptionCategory.AMOUNT_MISMATCH;
                summary = "Amount mismatch: internal=" + payment.getAmountMinor() + ", settlement=" + amountMinor;
            } else if (expectedFee != feeMinor) {
                discrepancy = ExceptionCategory.FEE_MISMATCH;
                summary = "Fee mismatch: expected=" + expectedFee + ", settlement=" + feeMinor;
            } else if (payment.getStatus() == PaymentStatus.PROCESSING || payment.getStatus() == PaymentStatus.UNKNOWN) {
                discrepancy = ExceptionCategory.DELAYED_SETTLEMENT;
                summary = "Settlement received while internal payment is in " + payment.getStatus();
            }

            matchStatus = discrepancy != null ? "DISCREPANCY" : "MATCHED";
            SettlementRecordEntity rec = recordRepository.saveAndFlush(new SettlementRecordEntity(
                    recordId, fileId, i + 1, providerRef, orderRef, amountMinor, feeMinor,
                    currency, settlementDate, matchStatus, matchedPaymentId, now));
            savedRecords.add(rec);

            if (discrepancy != null) {
                unmatched++;
                caseRepository.save(new ReconciliationCaseEntity(
                        UUID.randomUUID(), fileId, recordId, payment.getId(), discrepancy, summary, now));
                casesCreated++;
            } else {
                matched++;
            }
        }

        // Update file summary counts
        SettlementFileEntity updatedFile = fileRepository.save(new SettlementFileEntity(
                fileId, fileName, sha256, providerCode.name(), total, matched, unmatched, actorEmail, now
        ));

        auditService.record(actorId, actorEmail, roles, "RECONCILIATION_FILE_INGESTED",
                "SETTLEMENT_FILE", fileId.toString(), AuditOutcome.SUCCESS, ip,
                "{\"total\":" + total + ",\"matched\":" + matched + ",\"cases\":" + casesCreated + "}");

        return new UploadResultDto(updatedFile, savedRecords, casesCreated);
    }

    @Transactional(readOnly = true)
    public ReconciliationSummaryDto getSummary() {
        List<SettlementFileEntity> files = fileRepository.findAll();
        long totalImported = files.stream().mapToLong(SettlementFileEntity::getTotalRecords).sum();
        long matched = files.stream().mapToLong(SettlementFileEntity::getMatchedRecords).sum();
        long unmatched = files.stream().mapToLong(SettlementFileEntity::getUnmatchedRecords).sum();
        long openCases = caseRepository.countByStatus(ReconciliationCaseEntity.CaseStatus.OPEN)
                + caseRepository.countByStatus(ReconciliationCaseEntity.CaseStatus.INVESTIGATING);
        long resolvedCases = caseRepository.countByStatus(ReconciliationCaseEntity.CaseStatus.RESOLVED)
                + caseRepository.countByStatus(ReconciliationCaseEntity.CaseStatus.DISMISSED);
        double completion = totalImported == 0 ? 100.0 : Math.round((matched * 1000.0) / totalImported) / 10.0;
        return new ReconciliationSummaryDto(
                files.size(), totalImported, matched, unmatched, openCases, resolvedCases, completion
        );
    }

    @Transactional(readOnly = true)
    public List<SettlementFileEntity> listFiles() {
        return fileRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<SettlementRecordEntity> listFileRecords(UUID fileId) {
        return recordRepository.findBySettlementFileIdOrderByLineNumberAsc(fileId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReconciliationCaseEntity> listCases(ReconciliationCaseEntity.CaseStatus status, int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        var p = status != null
                ? caseRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : caseRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PagedResponse.from(p);
    }

    @Transactional
    public ReconciliationCaseEntity updateCase(UUID caseId, ReconciliationCaseEntity.CaseStatus nextStatus,
                                               String notes, UUID actorId, String actorEmail, String roles, String ip) {
        ReconciliationCaseEntity reconCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new DomainException("RECON_CASE_NOT_FOUND",
                        "Reconciliation case not found", HttpStatus.NOT_FOUND));
        reconCase.updateStatus(nextStatus, notes, actorEmail);
        caseRepository.save(reconCase);

        auditService.record(actorId, actorEmail, roles, "RECONCILIATION_CASE_UPDATED",
                "RECONCILIATION_CASE", caseId.toString(), AuditOutcome.SUCCESS, ip,
                "{\"status\":\"" + nextStatus.name() + "\"}");
        return reconCase;
    }

    private static String sha256Hex(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
