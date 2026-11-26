package com.payshield.payout;

import com.payshield.auth.PayShieldUserPrincipal;
import com.payshield.common.PagedResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payouts")
public class PayoutController {

    private final PayoutService payoutService;

    public PayoutController(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    public record KillSwitchRequest(boolean enabled, String reason) {}

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MERCHANT_ADMIN','OPERATIONS_ANALYST','PAYOUT_MAKER','PAYOUT_CHECKER','FINANCIAL_AUDITOR')")
    public PagedResponse<PayoutService.PayoutDto> list(
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            @RequestParam(required = false) PayoutStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return payoutService.list(actor, status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MERCHANT_ADMIN','PAYOUT_MAKER')")
    public PayoutService.PayoutDto create(
            @Valid @RequestBody PayoutService.CreatePayoutRequest req,
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            HttpServletRequest httpReq
    ) {
        return payoutService.createPayout(req, actor, httpReq.getRemoteAddr());
    }

    @PostMapping("/{payoutId}/decision")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PAYOUT_CHECKER')")
    public PayoutService.PayoutDto decide(
            @PathVariable UUID payoutId,
            @Valid @RequestBody PayoutService.DecisionRequest req,
            @AuthenticationPrincipal PayShieldUserPrincipal checker,
            HttpServletRequest httpReq
    ) {
        return payoutService.decide(payoutId, req, checker, httpReq.getRemoteAddr());
    }

    @PostMapping("/{payoutId}/execute")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATIONS_ANALYST','PAYOUT_CHECKER')")
    public PayoutService.PayoutDto execute(
            @PathVariable UUID payoutId,
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            HttpServletRequest httpReq
    ) {
        return payoutService.execute(payoutId, actor, httpReq.getRemoteAddr());
    }

    @GetMapping("/beneficiaries")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MERCHANT_ADMIN','OPERATIONS_ANALYST','PAYOUT_MAKER','PAYOUT_CHECKER')")
    public List<BeneficiaryEntity> beneficiaries(
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            @RequestParam(required = false) UUID merchantId
    ) {
        return payoutService.listBeneficiaries(actor, merchantId);
    }

    @GetMapping("/kill-switch")
    public PayoutKillSwitchEntity getKillSwitch() {
        return payoutService.getKillSwitch();
    }

    @PutMapping("/kill-switch")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PayoutKillSwitchEntity updateKillSwitch(
            @RequestBody KillSwitchRequest req,
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            HttpServletRequest httpReq
    ) {
        return payoutService.updateKillSwitch(req.enabled(), req.reason(), actor, httpReq.getRemoteAddr());
    }
}
