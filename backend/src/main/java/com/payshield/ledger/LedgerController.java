package com.payshield.ledger;

import com.payshield.auth.PayShieldUserPrincipal;
import com.payshield.common.PagedResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    public record ReverseTransactionRequest(@NotBlank String reason) {}

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATIONS_ANALYST','FINANCIAL_AUDITOR')")
    public LedgerService.FinancialSummaryDto summary() {
        return ledgerService.getFinancialSummary();
    }

    @GetMapping("/balances")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATIONS_ANALYST','FINANCIAL_AUDITOR')")
    public List<LedgerService.AccountBalanceDto> balances() {
        return ledgerService.listBalances();
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATIONS_ANALYST','FINANCIAL_AUDITOR')")
    public PagedResponse<LedgerService.JournalTransactionDto> transactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return ledgerService.listTransactions(page, size);
    }

    @PostMapping("/transactions/{txId}/reverse")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public LedgerService.JournalTransactionDto reverse(
            @PathVariable UUID txId,
            @Valid @RequestBody ReverseTransactionRequest req,
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            HttpServletRequest httpReq
    ) {
        return ledgerService.reverseTransaction(
                txId, req.reason(), actor.getUserId(), actor.getEmail(),
                String.join(",", actor.getRoles()), httpReq.getRemoteAddr()
        );
    }
}
