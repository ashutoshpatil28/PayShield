package com.payshield.merchant;

import com.payshield.audit.AuditOutcome;
import com.payshield.audit.AuditService;
import com.payshield.auth.PayShieldUserPrincipal;
import com.payshield.auth.RoleName;
import com.payshield.common.DomainException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final AuditService auditService;

    public MerchantService(MerchantRepository merchantRepository, AuditService auditService) {
        this.merchantRepository = merchantRepository;
        this.auditService = auditService;
    }

    public record MerchantDto(
            UUID id,
            String merchantCode,
            String legalName,
            String displayName,
            MerchantStatus status,
            String defaultCurrency,
            boolean demoData,
            Instant createdAt
    ) {
        public static MerchantDto from(MerchantEntity m) {
            return new MerchantDto(
                    m.getId(), m.getMerchantCode(), m.getLegalName(), m.getDisplayName(),
                    m.getStatus(), m.getDefaultCurrency(), m.isDemoData(), m.getCreatedAt()
            );
        }
    }

    public record CreateMerchantRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9_]{3,64}$") String merchantCode,
            @NotBlank String legalName,
            @NotBlank String displayName,
            @NotBlank @Pattern(regexp = "^(INR|USD|EUR|GBP)$") String defaultCurrency
    ) {}

    public record UpdateMerchantStatusRequest(MerchantStatus status) {}

    @Transactional(readOnly = true)
    public List<MerchantDto> listForUser(PayShieldUserPrincipal principal) {
        if (principal.isMerchantScoped()) {
            return merchantRepository.findById(principal.getMerchantId())
                    .map(m -> List.of(MerchantDto.from(m)))
                    .orElse(List.of());
        }
        return merchantRepository.findAll().stream().map(MerchantDto::from).toList();
    }

    @Transactional(readOnly = true)
    public MerchantDto getForUser(UUID merchantId, PayShieldUserPrincipal principal) {
        requireMerchantAccess(principal, merchantId);
        MerchantEntity entity = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new DomainException("MERCHANT_NOT_FOUND", "Merchant not found", HttpStatus.NOT_FOUND));
        return MerchantDto.from(entity);
    }

    @Transactional
    public MerchantDto create(CreateMerchantRequest req, PayShieldUserPrincipal actor, String ip) {
        if (merchantRepository.existsByMerchantCode(req.merchantCode())) {
            throw new DomainException("MERCHANT_EXISTS", "Merchant code already exists", HttpStatus.CONFLICT);
        }
        Instant now = Instant.now();
        MerchantEntity entity = new MerchantEntity(
                UUID.randomUUID(),
                req.merchantCode(),
                req.legalName().trim(),
                req.displayName().trim(),
                MerchantStatus.ACTIVE,
                req.defaultCurrency(),
                false,
                now,
                now
        );
        merchantRepository.save(entity);
        auditService.record(
                actor.getUserId(), actor.getEmail(), String.join(",", actor.getRoles()),
                "MERCHANT_CREATED", "MERCHANT", entity.getId().toString(),
                AuditOutcome.SUCCESS, ip, "{\"merchantCode\":\"" + entity.getMerchantCode() + "\"}"
        );
        return MerchantDto.from(entity);
    }

    @Transactional
    public MerchantDto updateStatus(UUID merchantId, MerchantStatus newStatus, PayShieldUserPrincipal actor, String ip) {
        MerchantEntity entity = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new DomainException("MERCHANT_NOT_FOUND", "Merchant not found", HttpStatus.NOT_FOUND));
        entity.setStatus(newStatus);
        merchantRepository.save(entity);
        auditService.record(
                actor.getUserId(), actor.getEmail(), String.join(",", actor.getRoles()),
                "MERCHANT_STATUS_UPDATED", "MERCHANT", entity.getId().toString(),
                AuditOutcome.SUCCESS, ip, "{\"status\":\"" + newStatus.name() + "\"}"
        );
        return MerchantDto.from(entity);
    }

    /**
     * Enforces tenant isolation: a merchant-scoped user can only access their own merchant.
     */
    public void requireMerchantAccess(PayShieldUserPrincipal principal, UUID targetMerchantId) {
        if (targetMerchantId == null) {
            throw new DomainException("MERCHANT_REQUIRED", "Merchant ID is required", HttpStatus.BAD_REQUEST);
        }
        if (principal.hasRole(RoleName.PLATFORM_ADMIN)
                || principal.hasRole(RoleName.OPERATIONS_ANALYST)
                || principal.hasRole(RoleName.RISK_ANALYST)
                || principal.hasRole(RoleName.FINANCIAL_AUDITOR)) {
            return;
        }
        if (principal.getMerchantId() == null || !principal.getMerchantId().equals(targetMerchantId)) {
            throw new DomainException("MERCHANT_ISOLATION_VIOLATION",
                    "Access denied for merchant " + targetMerchantId, HttpStatus.FORBIDDEN);
        }
    }

    @Transactional(readOnly = true)
    public MerchantEntity requireActiveMerchant(UUID merchantId) {
        MerchantEntity entity = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new DomainException("MERCHANT_NOT_FOUND", "Merchant not found", HttpStatus.NOT_FOUND));
        if (entity.getStatus() != MerchantStatus.ACTIVE) {
            throw new DomainException("MERCHANT_INACTIVE", "Merchant is not ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return entity;
    }
}
