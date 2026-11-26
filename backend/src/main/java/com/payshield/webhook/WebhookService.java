package com.payshield.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payshield.audit.AuditOutcome;
import com.payshield.audit.AuditService;
import com.payshield.common.DomainException;
import com.payshield.outbox.OutboxService;
import com.payshield.payment.PaymentService;
import com.payshield.payment.PaymentStatus;
import com.payshield.provider.ProviderCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class WebhookService {

    private static final long REPLAY_WINDOW_SECONDS = 300;

    private final Map<ProviderCode, String> secrets;
    private final ProviderEventRepository providerEventRepository;
    private final OutboxService outboxService;
    private final PaymentService paymentService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public WebhookService(
            @Value("${payshield.webhooks.alpha-secret}") String alphaSecret,
            @Value("${payshield.webhooks.beta-secret}") String betaSecret,
            @Value("${payshield.webhooks.gamma-secret}") String gammaSecret,
            ProviderEventRepository providerEventRepository,
            OutboxService outboxService,
            PaymentService paymentService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.secrets = Map.of(
                ProviderCode.PROVIDER_ALPHA, alphaSecret,
                ProviderCode.PROVIDER_BETA, betaSecret,
                ProviderCode.PROVIDER_GAMMA, gammaSecret
        );
        this.providerEventRepository = providerEventRepository;
        this.outboxService = outboxService;
        this.paymentService = paymentService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public record WebhookProcessResult(String status, String eventId, String message) {}

    @Transactional
    public WebhookProcessResult processWebhook(ProviderCode providerCode,
                                               String signatureHeader,
                                               String timestampHeader,
                                               String rawPayload,
                                               String ipAddress) {
        // 1. Validate timestamp freshness (replay protection)
        long timestampEpoch;
        try {
            timestampEpoch = Long.parseLong(timestampHeader);
        } catch (Exception ex) {
            throw new DomainException("INVALID_WEBHOOK_TIMESTAMP",
                    "Missing or invalid X-PayShield-Timestamp header", HttpStatus.UNAUTHORIZED);
        }
        long nowEpoch = Instant.now().getEpochSecond();
        if (Math.abs(nowEpoch - timestampEpoch) > REPLAY_WINDOW_SECONDS) {
            throw new DomainException("WEBHOOK_TIMESTAMP_EXPIRED",
                    "Webhook timestamp outside +/- 300s replay window", HttpStatus.UNAUTHORIZED);
        }

        // 2. Verify HMAC-SHA256 signature over "<timestamp>.<payload>"
        String secret = secrets.get(providerCode);
        String expectedSig = computeHmacSha256(secret, timestampHeader + "." + rawPayload);
        if (signatureHeader == null || !constantTimeEquals(expectedSig, signatureHeader.trim())) {
            auditService.record(null, "WEBHOOK:" + providerCode.name(), "SYSTEM",
                    "WEBHOOK_SIGNATURE_REJECTED", "WEBHOOK", providerCode.name(),
                    AuditOutcome.DENIED, ipAddress, null);
            throw new DomainException("INVALID_WEBHOOK_SIGNATURE",
                    "Invalid HMAC-SHA256 webhook signature", HttpStatus.UNAUTHORIZED);
        }

        // 3. Parse payload
        JsonNode root;
        try {
            root = objectMapper.readTree(rawPayload);
        } catch (Exception ex) {
            throw new DomainException("INVALID_WEBHOOK_JSON", "Malformed JSON webhook body", HttpStatus.BAD_REQUEST);
        }

        String eventId = textOrThrow(root, "eventId");
        String eventType = textOrThrow(root, "eventType");
        UUID paymentId = UUID.fromString(textOrThrow(root, "paymentId"));
        String providerReference = root.hasNonNull("providerReference") ? root.get("providerReference").asText() : null;
        String reason = root.hasNonNull("reason") ? root.get("reason").asText() : null;

        // 4. Inbox & provider_events deduplication
        String inboxKey = providerCode.name() + ":" + eventId;
        if (providerEventRepository.existsByProviderCodeAndProviderEventId(providerCode.name(), eventId)
                || !outboxService.tryClaimInbox("WEBHOOK_PROCESSOR", inboxKey)) {
            return new WebhookProcessResult("DUPLICATE", eventId, "Duplicate webhook safely ignored");
        }

        PaymentStatus targetStatus = switch (eventType) {
            case "PAYMENT_SUCCEEDED" -> PaymentStatus.SUCCEEDED;
            case "PAYMENT_FAILED" -> PaymentStatus.FAILED;
            default -> throw new DomainException("UNSUPPORTED_WEBHOOK_EVENT",
                    "Unsupported webhook eventType: " + eventType, HttpStatus.BAD_REQUEST);
        };

        paymentService.applyWebhookStatusUpdate(paymentId, targetStatus, providerReference, reason, eventId);

        Instant now = Instant.now();
        providerEventRepository.save(new ProviderEventEntity(
                UUID.randomUUID(),
                providerCode.name(),
                eventId,
                paymentId,
                eventType,
                rawPayload,
                true,
                ProviderEventEntity.ProcessingStatus.PROCESSED,
                null,
                now,
                now
        ));

        return new WebhookProcessResult("PROCESSED", eventId, "Webhook processed");
    }

    public static String computeHmacSha256(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute HMAC-SHA256", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String textOrThrow(JsonNode node, String field) {
        if (!node.hasNonNull(field) || node.get(field).asText().isBlank()) {
            throw new DomainException("MISSING_WEBHOOK_FIELD",
                    "Missing required webhook field: " + field, HttpStatus.BAD_REQUEST);
        }
        return node.get(field).asText();
    }
}
