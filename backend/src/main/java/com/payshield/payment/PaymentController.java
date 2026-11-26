package com.payshield.payment;

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
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MERCHANT_ADMIN','OPERATIONS_ANALYST')")
    public PaymentDtos.PaymentDto create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentDtos.CreatePaymentRequest request,
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            HttpServletRequest httpReq
    ) {
        return paymentService.createPayment(request, idempotencyKey, actor, httpReq.getRemoteAddr());
    }

    @GetMapping
    public PagedResponse<PaymentDtos.PaymentDto> list(
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return paymentService.listPayments(actor, merchantId, status, provider, search, page, size, sortBy, sortDir);
    }

    @GetMapping("/{paymentId}")
    public PaymentDtos.PaymentDetailDto get(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal PayShieldUserPrincipal actor
    ) {
        return paymentService.getPaymentDetail(paymentId, actor);
    }

    @GetMapping("/{paymentId}/events")
    public List<PaymentDtos.PaymentEventDto> events(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal PayShieldUserPrincipal actor
    ) {
        return paymentService.getPaymentEvents(paymentId, actor);
    }

    @PostMapping("/{paymentId}/verify")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATIONS_ANALYST','MERCHANT_ADMIN')")
    public PaymentDtos.PaymentDto verifyStatus(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            HttpServletRequest httpReq
    ) {
        return paymentService.verifyPaymentStatus(paymentId, actor, httpReq.getRemoteAddr());
    }
}
