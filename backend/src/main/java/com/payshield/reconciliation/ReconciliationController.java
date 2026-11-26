package com.payshield.reconciliation;

import com.payshield.auth.PayShieldUserPrincipal;
import com.payshield.common.PagedResponse;
import com.payshield.provider.ProviderCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    public record UploadCsvRequest(
            @NotBlank String fileName,
            @NotNull ProviderCode providerCode,
            @NotBlank String csvContent
    ) {}

    public record UpdateCaseRequest(
            @NotNull ReconciliationCaseEntity.CaseStatus status,
            String resolutionNotes
    ) {}

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATIONS_ANALYST','FINANCIAL_AUDITOR')")
    public ReconciliationService.ReconciliationSummaryDto summary() {
        return reconciliationService.getSummary();
    }

    @GetMapping("/files")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATIONS_ANALYST','FINANCIAL_AUDITOR')")
    public List<SettlementFileEntity> listFiles() {
        return reconciliationService.listFiles();
    }

    @GetMapping("/files/{fileId}/records")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATIONS_ANALYST','FINANCIAL_AUDITOR')")
    public List<SettlementRecordEntity> listFileRecords(@PathVariable UUID fileId) {
        return reconciliationService.listFileRecords(fileId);
    }

    @PostMapping("/files")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATIONS_ANALYST')")
    public ReconciliationService.UploadResultDto uploadCsv(
            @Valid @RequestBody UploadCsvRequest req,
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            HttpServletRequest httpReq
    ) {
        return reconciliationService.ingestCsv(
                req.fileName(), req.providerCode(), req.csvContent(),
                actor.getUserId(), actor.getEmail(), String.join(",", actor.getRoles()),
                httpReq.getRemoteAddr()
        );
    }

    @GetMapping("/cases")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATIONS_ANALYST','FINANCIAL_AUDITOR')")
    public PagedResponse<ReconciliationCaseEntity> listCases(
            @RequestParam(required = false) ReconciliationCaseEntity.CaseStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return reconciliationService.listCases(status, page, size);
    }

    @PatchMapping("/cases/{caseId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATIONS_ANALYST')")
    public ReconciliationCaseEntity updateCase(
            @PathVariable UUID caseId,
            @Valid @RequestBody UpdateCaseRequest req,
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            HttpServletRequest httpReq
    ) {
        return reconciliationService.updateCase(
                caseId, req.status(), req.resolutionNotes(),
                actor.getUserId(), actor.getEmail(), String.join(",", actor.getRoles()),
                httpReq.getRemoteAddr()
        );
    }
}
