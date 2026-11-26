package com.payshield.webhook;

import com.payshield.common.PagedResponse;
import com.payshield.provider.ProviderCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class WebhookController {

    private final WebhookService webhookService;
    private final ProviderEventRepository providerEventRepository;

    public WebhookController(WebhookService webhookService,
                             ProviderEventRepository providerEventRepository) {
        this.webhookService = webhookService;
        this.providerEventRepository = providerEventRepository;
    }

    @PostMapping("/webhooks/{providerCode}")
    public WebhookService.WebhookProcessResult receive(
            @PathVariable ProviderCode providerCode,
            @RequestHeader(value = "X-PayShield-Signature", required = false) String signature,
            @RequestHeader(value = "X-PayShield-Timestamp", required = false) String timestamp,
            @RequestBody String rawBody,
            HttpServletRequest httpReq
    ) {
        return webhookService.processWebhook(providerCode, signature, timestamp, rawBody, httpReq.getRemoteAddr());
    }

    @GetMapping("/webhook-events")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATIONS_ANALYST','FINANCIAL_AUDITOR')")
    public PagedResponse<ProviderEventEntity> listWebhookEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return PagedResponse.from(
                providerEventRepository.findAllByOrderByReceivedAtDesc(
                        PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)))
        );
    }
}
