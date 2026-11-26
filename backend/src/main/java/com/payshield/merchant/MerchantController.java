package com.payshield.merchant;

import com.payshield.auth.PayShieldUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    public List<MerchantService.MerchantDto> list(@AuthenticationPrincipal PayShieldUserPrincipal principal) {
        return merchantService.listForUser(principal);
    }

    @GetMapping("/{merchantId}")
    public MerchantService.MerchantDto get(
            @PathVariable UUID merchantId,
            @AuthenticationPrincipal PayShieldUserPrincipal principal
    ) {
        return merchantService.getForUser(merchantId, principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public MerchantService.MerchantDto create(
            @Valid @RequestBody MerchantService.CreateMerchantRequest req,
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            HttpServletRequest httpReq
    ) {
        return merchantService.create(req, actor, httpReq.getRemoteAddr());
    }

    @PatchMapping("/{merchantId}/status")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public MerchantService.MerchantDto updateStatus(
            @PathVariable UUID merchantId,
            @RequestBody MerchantService.UpdateMerchantStatusRequest req,
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            HttpServletRequest httpReq
    ) {
        return merchantService.updateStatus(merchantId, req.status(), actor, httpReq.getRemoteAddr());
    }
}
