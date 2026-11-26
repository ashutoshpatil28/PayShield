package com.payshield.payout;

import com.payshield.audit.AuditOutcome;
import com.payshield.audit.AuditService;
import com.payshield.auth.PayShieldUserPrincipal;
import com.payshield.common.DomainException;
import com.payshield.common.Money;
import com.payshield.common.PagedResponse;
import com.payshield.ledger.JournalLineEntity;
import com.payshield.ledger.LedgerService;
import com.payshield.merchant.MerchantService;
import com.payshield.outbox.OutboxService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class PayoutService {

    private static final long PER_TX_LIMIT_MINOR = 50_000_00L;   // ₹500,000.00
    private static final long DAILY_LIMIT_MINOR = 200_000_00L;   // ₹2,000,000.00

    private final BeneficiaryRepository beneficiaryRepository;
    private final PayoutRequestRepository payoutRepository;
    private final PayoutApprovalRepository approvalRepository;
    private final PayoutKillSwitchRepository killSwitchRepository;
    private final MerchantService merchantService;
    private final LedgerService ledgerService;
    private final OutboxService outboxService;
    private final AuditService auditService;

    public PayoutService(
            BeneficiaryRepository beneficiaryRepository,
            PayoutRequestRepository payoutRepository,
            PayoutApprovalRepository approvalRepository,
            PayoutKillSwitchRepository killSwitchRepository,
            MerchantService merchantService,
            LedgerService ledgerService,
            OutboxService outboxService,
            AuditService auditService
    ) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.payoutRepository = payoutRepository;
        this.approvalRepository = approvalRepository;
        this.killSwitchRepository = killSwitchRepository;
        this.merchantService = merchantService;
        this.ledgerService = ledgerService;
        this.outboxService = outboxService;
        this.auditService = auditService;
    }

    public record CreatePayoutRequest(
            @NotNull UUID merchantId,
            @NotNull UUID beneficiaryId,
            @Min(1) long amountMinor,
            @NotBlank @Pattern(regexp = "^(INR|USD|EUR|GBP)$") String currency,
            @NotBlank String purpose
    ) {}

    public record DecisionRequest(
            boolean approved,
            String comments
    ) {}

    public record PayoutDto(
            UUID id,
            UUID merchantId,
            UUID beneficiaryId,
            String beneficiaryName,
            long amountMinor,
            String currency,
            PayoutStatus status,
            String purpose,
            UUID makerUserId,
            int riskScore,
            Instant approvalExpiresAt,
            String failureReason,
            boolean demoData,
            Instant createdAt,
            Instant updatedAt
    ) {}

    @Transactional
    public PayoutDto createPayout(CreatePayoutRequest req, PayShieldUserPrincipal actor, String ip) {
        merchantService.requireMerchantAccess(actor, req.merchantId());
        merchantService.requireActiveMerchant(req.merchantId());
        Money.positive(req.amountMinor(), req.currency());

        requireKillSwitchInactive();

        BeneficiaryEntity beneficiary = beneficiaryRepository.findById(req.beneficiaryId())
                .orElseThrow(() -> new DomainException("BENEFICIARY_NOT_FOUND",
                        "Beneficiary not found", HttpStatus.NOT_FOUND));
        if (!beneficiary.getMerchantId().equals(req.merchantId())) {
            throw new DomainException("BENEFICIARY_MERCHANT_MISMATCH",
                    "Beneficiary does not belong to merchant", HttpStatus.FORBIDDEN);
        }
        if (!beneficiary.isAllowlisted()) {
            throw new DomainException("BENEFICIARY_NOT_ALLOWLISTED",
                    "Beneficiary is not on the approved allowlist", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        if (req.amountMinor() > PER_TX_LIMIT_MINOR) {
            throw new DomainException("PAYOUT_PER_TX_LIMIT_EXCEEDED",
                    "Payout exceeds per-transaction limit of " + PER_TX_LIMIT_MINOR + " minor units",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        long dailyUsed = payoutRepository.sumMerchantDailyVolume(
                req.merchantId(), Instant.now().minus(24, ChronoUnit.HOURS));
        if (Math.addExact(dailyUsed, req.amountMinor()) > DAILY_LIMIT_MINOR) {
            throw new DomainException("PAYOUT_DAILY_LIMIT_EXCEEDED",
                    "Payout exceeds 24h merchant daily limit", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Instant now = Instant.now();
        PayoutRequestEntity payout = new PayoutRequestEntity(
                UUID.randomUUID(),
                req.merchantId(),
                req.beneficiaryId(),
                req.amountMinor(),
                req.currency().toUpperCase(),
                req.purpose().trim(),
                actor.getUserId(),
                false,
                now
        );
        int computedRisk = req.amountMinor() >= 25_000_00L ? 45 : 10;
        payout.setRiskScore(computedRisk);
        payout.transitionTo(PayoutStatus.VALIDATED, null);
        payout.setApprovalExpiresAt(now.plus(24, ChronoUnit.HOURS));
        payout.transitionTo(PayoutStatus.PENDING_MAKER_CHECKER, null);
        payoutRepository.save(payout);

        outboxService.enqueue("PAYOUT_PENDING_APPROVAL", "PAYOUT", payout.getId().toString(),
                "{\"payoutId\":\"" + payout.getId() + "\",\"merchantId\":\"" + payout.getMerchantId()
                        + "\",\"amountMinor\":" + payout.getAmountMinor() + "}");

        auditService.record(actor.getUserId(), actor.getEmail(), String.join(",", actor.getRoles()),
                "PAYOUT_REQUESTED", "PAYOUT", payout.getId().toString(), AuditOutcome.SUCCESS, ip,
                "{\"amountMinor\":" + payout.getAmountMinor() + "}");

        return toDto(payout, beneficiary.getBeneficiaryName());
    }

    @Transactional
    public PayoutDto decide(UUID payoutId, DecisionRequest req, PayShieldUserPrincipal checker, String ip) {
        PayoutRequestEntity payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new DomainException("PAYOUT_NOT_FOUND", "Payout not found", HttpStatus.NOT_FOUND));
        merchantService.requireMerchantAccess(checker, payout.getMerchantId());

        if (payout.getMakerUserId().equals(checker.getUserId())) {
            auditService.record(checker.getUserId(), checker.getEmail(), String.join(",", checker.getRoles()),
                    "PAYOUT_SELF_APPROVAL_BLOCKED", "PAYOUT", payoutId.toString(),
                    AuditOutcome.DENIED, ip, null);
            throw new DomainException("SEPARATION_OF_DUTIES_VIOLATION",
                    "A payout maker cannot approve or reject their own payout request", HttpStatus.FORBIDDEN);
        }

        if (payout.getStatus() != PayoutStatus.PENDING_MAKER_CHECKER) {
            throw new DomainException("PAYOUT_NOT_PENDING_APPROVAL",
                    "Payout is in state " + payout.getStatus(), HttpStatus.CONFLICT);
        }
        if (payout.getApprovalExpiresAt() != null && payout.getApprovalExpiresAt().isBefore(Instant.now())) {
            throw new DomainException("PAYOUT_APPROVAL_EXPIRED",
                    "Payout approval window has expired", HttpStatus.CONFLICT);
        }

        PayoutStatus target = req.approved() ? PayoutStatus.APPROVED : PayoutStatus.REJECTED;
        payout.transitionTo(target, req.approved() ? null : req.comments());
        payoutRepository.save(payout);

        approvalRepository.save(new PayoutApprovalEntity(
                UUID.randomUUID(),
                payoutId,
                payout.getMakerUserId(),
                checker.getUserId(),
                req.approved() ? "APPROVED" : "REJECTED",
                req.comments(),
                Instant.now()
        ));

        auditService.record(checker.getUserId(), checker.getEmail(), String.join(",", checker.getRoles()),
                req.approved() ? "PAYOUT_APPROVED" : "PAYOUT_REJECTED",
                "PAYOUT", payoutId.toString(), AuditOutcome.SUCCESS, ip,
                "{\"comments\":\"" + (req.comments() == null ? "" : req.comments()) + "\"}");

        String benName = beneficiaryRepository.findById(payout.getBeneficiaryId())
                .map(BeneficiaryEntity::getBeneficiaryName).orElse("Unknown");
        return toDto(payout, benName);
    }

    @Transactional
    public PayoutDto execute(UUID payoutId, PayShieldUserPrincipal actor, String ip) {
        requireKillSwitchInactive();

        PayoutRequestEntity payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new DomainException("PAYOUT_NOT_FOUND", "Payout not found", HttpStatus.NOT_FOUND));
        merchantService.requireMerchantAccess(actor, payout.getMerchantId());

        if (payout.getStatus() != PayoutStatus.APPROVED) {
            auditService.record(actor.getUserId(), actor.getEmail(), String.join(",", actor.getRoles()),
                    "PAYOUT_UNAUTHORIZED_EXECUTION_REJECTED", "PAYOUT", payoutId.toString(),
                    AuditOutcome.DENIED, ip, "{\"status\":\"" + payout.getStatus() + "\"}");
            throw new DomainException("PAYOUT_NOT_APPROVED",
                    "Payout execution requires APPROVED state; current state is " + payout.getStatus(),
                    HttpStatus.CONFLICT);
        }
        if (payout.getApprovalExpiresAt() != null && payout.getApprovalExpiresAt().isBefore(Instant.now())) {
            throw new DomainException("PAYOUT_APPROVAL_EXPIRED",
                    "Approved payout has expired and cannot be executed", HttpStatus.CONFLICT);
        }

        payout.transitionTo(PayoutStatus.EXECUTION_PENDING, null);
        payout.transitionTo(PayoutStatus.EXECUTED, null);
        payoutRepository.save(payout);

        String merchantPayable = "LIABILITY:MERCHANT_PAYABLE:" + payout.getMerchantId() + ":" + payout.getCurrency();
        String settlementBank = "ASSET:BANK_SETTLEMENT:" + payout.getCurrency();
        ledgerService.postJournal(
                "PAYOUT",
                payoutId.toString(),
                "PAYOUT_SETTLE",
                "Payout execution " + payoutId,
                payout.getCurrency(),
                List.of(
                        new LedgerService.PostingLineSpec(merchantPayable, JournalLineEntity.EntryType.DEBIT, payout.getAmountMinor()),
                        new LedgerService.PostingLineSpec(settlementBank, JournalLineEntity.EntryType.CREDIT, payout.getAmountMinor())
                ),
                actor.getEmail(),
                null
        );

        outboxService.enqueue("PAYOUT_EXECUTED", "PAYOUT", payoutId.toString(),
                "{\"payoutId\":\"" + payoutId + "\",\"amountMinor\":" + payout.getAmountMinor() + "}");

        auditService.record(actor.getUserId(), actor.getEmail(), String.join(",", actor.getRoles()),
                "PAYOUT_EXECUTED", "PAYOUT", payoutId.toString(), AuditOutcome.SUCCESS, ip, null);

        String benName = beneficiaryRepository.findById(payout.getBeneficiaryId())
                .map(BeneficiaryEntity::getBeneficiaryName).orElse("Unknown");
        return toDto(payout, benName);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PayoutDto> list(PayShieldUserPrincipal actor, PayoutStatus status, int page, int size) {
        UUID merchantFilter = actor.isMerchantScoped() ? actor.getMerchantId() : null;
        var p = payoutRepository.search(merchantFilter, status,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        return PagedResponse.from(p.map(req -> {
            String benName = beneficiaryRepository.findById(req.getBeneficiaryId())
                    .map(BeneficiaryEntity::getBeneficiaryName).orElse("Unknown");
            return toDto(req, benName);
        }));
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryEntity> listBeneficiaries(PayShieldUserPrincipal actor, UUID merchantId) {
        UUID effective = actor.isMerchantScoped() ? actor.getMerchantId() : merchantId;
        if (effective != null) {
            merchantService.requireMerchantAccess(actor, effective);
            return beneficiaryRepository.findByMerchantIdOrderByBeneficiaryNameAsc(effective);
        }
        return beneficiaryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public PayoutKillSwitchEntity getKillSwitch() {
        return killSwitchRepository.findById("GLOBAL").orElseThrow();
    }

    @Transactional
    public PayoutKillSwitchEntity updateKillSwitch(boolean enabled, String reason, PayShieldUserPrincipal actor, String ip) {
        PayoutKillSwitchEntity ks = getKillSwitch();
        ks.update(enabled, reason, actor.getEmail());
        killSwitchRepository.save(ks);
        auditService.record(actor.getUserId(), actor.getEmail(), String.join(",", actor.getRoles()),
                "PAYOUT_KILL_SWITCH_UPDATED", "KILL_SWITCH", "GLOBAL", AuditOutcome.SUCCESS, ip,
                "{\"enabled\":" + enabled + "}");
        return ks;
    }

    private void requireKillSwitchInactive() {
        if (getKillSwitch().isEnabled()) {
            throw new DomainException("PAYOUT_KILL_SWITCH_ACTIVE",
                    "Payout operations are halted by the emergency kill switch", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private static PayoutDto toDto(PayoutRequestEntity p, String benName) {
        return new PayoutDto(
                p.getId(), p.getMerchantId(), p.getBeneficiaryId(), benName,
                p.getAmountMinor(), p.getCurrency(), p.getStatus(), p.getPurpose(),
                p.getMakerUserId(), p.getRiskScore(), p.getApprovalExpiresAt(),
                p.getFailureReason(), p.isDemoData(), p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
