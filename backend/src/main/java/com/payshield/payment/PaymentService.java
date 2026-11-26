package com.payshield.payment;

import com.payshield.audit.AuditOutcome;
import com.payshield.audit.AuditService;
import com.payshield.auth.PayShieldUserPrincipal;
import com.payshield.common.CorrelationIdFilter;
import com.payshield.common.DomainException;
import com.payshield.common.Money;
import com.payshield.common.PagedResponse;
import com.payshield.merchant.MerchantService;
import com.payshield.outbox.OutboxService;
import com.payshield.provider.PaymentProvider;
import com.payshield.provider.ProviderCode;
import com.payshield.provider.SimulatedProviderGateway;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentEventRepository eventRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final IdempotencyLockService idempotencyLockService;
    private final MerchantService merchantService;
    private final SimulatedProviderGateway providerGateway;
    private final OutboxService outboxService;
    private final AuditService auditService;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository attemptRepository,
            PaymentEventRepository eventRepository,
            IdempotencyRecordRepository idempotencyRepository,
            IdempotencyLockService idempotencyLockService,
            MerchantService merchantService,
            SimulatedProviderGateway providerGateway,
            OutboxService outboxService,
            AuditService auditService
    ) {
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.eventRepository = eventRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.idempotencyLockService = idempotencyLockService;
        this.merchantService = merchantService;
        this.providerGateway = providerGateway;
        this.outboxService = outboxService;
        this.auditService = auditService;
    }

    @Transactional
    public PaymentDtos.PaymentDto createPayment(PaymentDtos.CreatePaymentRequest req,
                                                String idempotencyKey,
                                                PayShieldUserPrincipal actor,
                                                String ipAddress) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new DomainException("IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key header is required", HttpStatus.BAD_REQUEST);
        }
        String trimmedKey = idempotencyKey.trim();
        merchantService.requireMerchantAccess(actor, req.merchantId());
        merchantService.requireActiveMerchant(req.merchantId());

        // Validate Money invariant (positive minor units & valid ISO-4217 currency)
        Money.positive(req.amountMinor(), req.currency());

        ProviderCode selectedProvider = req.preferredProvider() != null
                ? req.preferredProvider()
                : ProviderCode.PROVIDER_ALPHA;

        String requestHash = hashRequest(req, selectedProvider);

        // Idempotency claim in isolated REQUIRES_NEW transaction
        IdempotencyLockService.ClaimResult claim;
        try {
            claim = idempotencyLockService.claimOrGet(req.merchantId(), trimmedKey, requestHash);
        } catch (IdempotencyLockService.ConcurrentIdempotencyRaceException race) {
            IdempotencyRecordEntity raced = idempotencyLockService.getValidatedExisting(req.merchantId(), trimmedKey, requestHash);
            return handleExistingIdempotency(raced, requestHash);
        }
        if (claim.alreadyExisted()) {
            return handleExistingIdempotency(claim.record(), requestHash);
        }
        IdempotencyRecordEntity idemRecord = claim.record();

        Instant now = Instant.now();
        UUID paymentId = UUID.randomUUID();
        PaymentEntity payment = new PaymentEntity(
                paymentId,
                req.merchantId(),
                req.merchantOrderRef().trim(),
                req.amountMinor(),
                req.currency().toUpperCase(),
                selectedProvider.name(),
                trimmedKey,
                false,
                now
        );
        paymentRepository.save(payment);
        recordEvent(paymentId, null, PaymentStatus.CREATED.name(), "PAYMENT_CREATED", actor.getEmail(), null);

        // Transition CREATED -> PROCESSING
        payment.transitionTo(PaymentStatus.PROCESSING, null, null);
        paymentRepository.save(payment);
        recordEvent(paymentId, PaymentStatus.CREATED.name(), PaymentStatus.PROCESSING.name(),
                "PAYMENT_PROCESSING_STARTED", actor.getEmail(),
                "{\"provider\":\"" + selectedProvider.name() + "\"}");

        // Execute provider attempt with provider-side idempotency request ID
        String providerRequestId = "REQ_" + paymentId.toString().replace("-", "").substring(0, 16);
        PaymentProvider.ChargeResult result = providerGateway.charge(new PaymentProvider.ChargeCommand(
                selectedProvider,
                providerRequestId,
                req.amountMinor(),
                req.currency().toUpperCase(),
                req.merchantOrderRef()
        ));

        attemptRepository.save(new PaymentAttemptEntity(
                UUID.randomUUID(),
                paymentId,
                1,
                selectedProvider.name(),
                providerRequestId,
                result.providerReference(),
                result.outcome().name(),
                result.latencyMs(),
                result.errorCode(),
                result.errorMessage(),
                Instant.now()
        ));

        PaymentStatus targetStatus = switch (result.outcome()) {
            case SUCCEEDED -> PaymentStatus.SUCCEEDED;
            case TIMEOUT_UNKNOWN -> PaymentStatus.UNKNOWN; // NEVER auto-fail on timeout!
            case DECLINED, UNAVAILABLE, INVALID_RESPONSE -> PaymentStatus.FAILED;
        };

        payment.transitionTo(targetStatus, result.providerReference(), result.errorMessage());
        paymentRepository.save(payment);
        recordEvent(paymentId, PaymentStatus.PROCESSING.name(), targetStatus.name(),
                "PROVIDER_ATTEMPT_" + result.outcome().name(), actor.getEmail(),
                "{\"providerReference\":\"" + (result.providerReference() == null ? "" : result.providerReference()) + "\"}");

        idemRecord.markCompleted(paymentId);
        idempotencyRepository.save(idemRecord);

        // Persist transactional outbox event in the same DB transaction
        outboxService.enqueue(
                "PAYMENT_" + targetStatus.name(),
                "PAYMENT",
                paymentId.toString(),
                buildOutboxPayload(payment)
        );

        auditService.record(
                actor.getUserId(), actor.getEmail(), String.join(",", actor.getRoles()),
                "PAYMENT_INITIATED", "PAYMENT", paymentId.toString(),
                AuditOutcome.SUCCESS, ipAddress,
                "{\"status\":\"" + targetStatus.name() + "\",\"amountMinor\":" + payment.getAmountMinor() + "}"
        );

        return PaymentDtos.PaymentDto.from(payment, false);
    }

    @Transactional
    public PaymentDtos.PaymentDto verifyPaymentStatus(UUID paymentId,
                                                      PayShieldUserPrincipal actor,
                                                      String ipAddress) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new DomainException("PAYMENT_NOT_FOUND", "Payment not found", HttpStatus.NOT_FOUND));
        merchantService.requireMerchantAccess(actor, payment.getMerchantId());

        if (payment.getStatus() != PaymentStatus.UNKNOWN && payment.getStatus() != PaymentStatus.PROCESSING) {
            throw new DomainException("VERIFICATION_NOT_APPLICABLE",
                    "Status verification is only applicable to UNKNOWN or PROCESSING payments",
                    HttpStatus.CONFLICT);
        }

        List<PaymentAttemptEntity> attempts = attemptRepository.findByPaymentIdOrderByAttemptNumberAsc(paymentId);
        if (attempts.isEmpty()) {
            throw new DomainException("NO_ATTEMPTS_FOUND", "No provider attempts found to verify", HttpStatus.CONFLICT);
        }
        PaymentAttemptEntity lastAttempt = attempts.get(attempts.size() - 1);
        ProviderCode provider = ProviderCode.valueOf(lastAttempt.getProviderCode());

        PaymentProvider.VerificationResult verification =
                providerGateway.verifyStatus(provider, lastAttempt.getProviderRequestId());

        PaymentStatus previousStatus = payment.getStatus();
        PaymentStatus nextStatus = switch (verification.outcome()) {
            case SUCCEEDED -> PaymentStatus.SUCCEEDED;
            case DECLINED, NOT_FOUND -> PaymentStatus.FAILED;
            case STILL_UNKNOWN -> PaymentStatus.UNKNOWN;
        };

        if (nextStatus != previousStatus) {
            String failReason = nextStatus == PaymentStatus.FAILED ? verification.message() : null;
            payment.transitionTo(nextStatus, verification.providerReference(), failReason);
            paymentRepository.save(payment);
            recordEvent(paymentId, previousStatus.name(), nextStatus.name(),
                    "PROVIDER_STATUS_VERIFIED", actor.getEmail(),
                    "{\"verificationOutcome\":\"" + verification.outcome().name() + "\"}");

            outboxService.enqueue(
                    "PAYMENT_" + nextStatus.name(),
                    "PAYMENT",
                    paymentId.toString(),
                    buildOutboxPayload(payment)
            );
        } else {
            recordEvent(paymentId, previousStatus.name(), nextStatus.name(),
                    "PROVIDER_STATUS_VERIFIED_UNCHANGED", actor.getEmail(),
                    "{\"verificationOutcome\":\"" + verification.outcome().name() + "\"}");
        }

        auditService.record(
                actor.getUserId(), actor.getEmail(), String.join(",", actor.getRoles()),
                "PAYMENT_STATUS_VERIFIED", "PAYMENT", paymentId.toString(),
                AuditOutcome.SUCCESS, ipAddress,
                "{\"from\":\"" + previousStatus + "\",\"to\":\"" + nextStatus + "\"}"
        );

        return PaymentDtos.PaymentDto.from(payment, false);
    }

    @Transactional
    public void applyWebhookStatusUpdate(UUID paymentId, PaymentStatus targetStatus,
                                         String providerReference, String reason, String eventId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new DomainException("PAYMENT_NOT_FOUND", "Payment not found", HttpStatus.NOT_FOUND));

        if (payment.getStatus() == targetStatus) {
            return;
        }
        if (!PaymentStateMachine.canTransition(payment.getStatus(), targetStatus)) {
            // Out-of-order or already terminal: record event for investigation without crashing
            recordEvent(paymentId, payment.getStatus().name(), payment.getStatus().name(),
                    "WEBHOOK_IGNORED_OUT_OF_ORDER", "WEBHOOK_PROCESSOR",
                    "{\"target\":\"" + targetStatus + "\",\"eventId\":\"" + eventId + "\"}");
            return;
        }

        String from = payment.getStatus().name();
        payment.transitionTo(targetStatus, providerReference, reason);
        paymentRepository.save(payment);
        recordEvent(paymentId, from, targetStatus.name(),
                "WEBHOOK_" + targetStatus.name(), "WEBHOOK_PROCESSOR",
                "{\"eventId\":\"" + eventId + "\"}");

        outboxService.enqueue(
                "PAYMENT_" + targetStatus.name(),
                "PAYMENT",
                paymentId.toString(),
                buildOutboxPayload(payment)
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<PaymentDtos.PaymentDto> listPayments(
            PayShieldUserPrincipal actor,
            UUID merchantIdFilter,
            PaymentStatus status,
            String provider,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {
        UUID effectiveMerchantId = actor.isMerchantScoped() ? actor.getMerchantId() : merchantIdFilter;
        if (actor.isMerchantScoped() && merchantIdFilter != null && !merchantIdFilter.equals(actor.getMerchantId())) {
            merchantService.requireMerchantAccess(actor, merchantIdFilter);
        }
        String normProvider = (provider == null || provider.isBlank()) ? null : provider.trim();
        String normSearch = (search == null || search.isBlank()) ? null : search.trim();

        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String safeSort = switch (sortBy) {
            case "amountMinor", "status", "selectedProvider", "createdAt" -> sortBy;
            default -> "createdAt";
        };
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), Sort.by(dir, safeSort));
        org.springframework.data.jpa.domain.Specification<PaymentEntity> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (effectiveMerchantId != null) {
                predicates.add(cb.equal(root.get("merchantId"), effectiveMerchantId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (normProvider != null) {
                predicates.add(cb.equal(root.get("selectedProvider"), normProvider));
            }
            if (normSearch != null) {
                String pattern = "%" + normSearch.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("merchantOrderRef")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("providerReference"), "")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return PagedResponse.from(
                paymentRepository.findAll(spec, pageable)
                        .map(p -> PaymentDtos.PaymentDto.from(p, false))
        );
    }

    @Transactional(readOnly = true)
    public PaymentDtos.PaymentDetailDto getPaymentDetail(UUID paymentId, PayShieldUserPrincipal actor) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new DomainException("PAYMENT_NOT_FOUND", "Payment not found", HttpStatus.NOT_FOUND));
        merchantService.requireMerchantAccess(actor, payment.getMerchantId());
        var attempts = attemptRepository.findByPaymentIdOrderByAttemptNumberAsc(paymentId).stream()
                .map(PaymentDtos.PaymentAttemptDto::from).toList();
        var events = eventRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId).stream()
                .map(PaymentDtos.PaymentEventDto::from).toList();
        return new PaymentDtos.PaymentDetailDto(PaymentDtos.PaymentDto.from(payment, false), attempts, events);
    }

    @Transactional(readOnly = true)
    public List<PaymentDtos.PaymentEventDto> getPaymentEvents(UUID paymentId, PayShieldUserPrincipal actor) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new DomainException("PAYMENT_NOT_FOUND", "Payment not found", HttpStatus.NOT_FOUND));
        merchantService.requireMerchantAccess(actor, payment.getMerchantId());
        return eventRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId).stream()
                .map(PaymentDtos.PaymentEventDto::from).toList();
    }

    private PaymentDtos.PaymentDto handleExistingIdempotency(IdempotencyRecordEntity existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new DomainException(
                    "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD",
                    "Idempotency-Key was already used with a different payment payload",
                    HttpStatus.CONFLICT
            );
        }
        if (existing.getPaymentId() == null) {
            throw new DomainException(
                    "IDEMPOTENCY_IN_PROGRESS",
                    "Payment with this Idempotency-Key is currently being processed",
                    HttpStatus.CONFLICT
            );
        }
        PaymentEntity existingPayment = paymentRepository.findById(existing.getPaymentId())
                .orElseThrow(() -> new DomainException("PAYMENT_NOT_FOUND", "Payment not found", HttpStatus.NOT_FOUND));
        return PaymentDtos.PaymentDto.from(existingPayment, true);
    }

    private void recordEvent(UUID paymentId, String fromStatus, String toStatus,
                             String eventType, String actor, String detailsJson) {
        eventRepository.save(new PaymentEventEntity(
                UUID.randomUUID(), paymentId, fromStatus, toStatus, eventType,
                actor, CorrelationIdFilter.currentCorrelationId(), detailsJson, Instant.now()
        ));
    }

    private String buildOutboxPayload(PaymentEntity p) {
        return "{\"paymentId\":\"" + p.getId()
                + "\",\"merchantId\":\"" + p.getMerchantId()
                + "\",\"amountMinor\":" + p.getAmountMinor()
                + ",\"currency\":\"" + p.getCurrency()
                + "\",\"status\":\"" + p.getStatus().name()
                + "\",\"provider\":\"" + p.getSelectedProvider() + "\"}";
    }

    private static String hashRequest(PaymentDtos.CreatePaymentRequest req, ProviderCode provider) {
        String canonical = req.merchantId() + "|" + req.merchantOrderRef().trim()
                + "|" + req.amountMinor() + "|" + req.currency().toUpperCase() + "|" + provider.name();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
