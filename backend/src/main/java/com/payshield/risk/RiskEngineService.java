package com.payshield.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payshield.audit.AuditOutcome;
import com.payshield.audit.AuditService;
import com.payshield.common.DomainException;
import com.payshield.common.PagedResponse;
import com.payshield.outbox.OutboxService;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic rule-based risk scoring and alert engine.
 * Note: This is an internal simulation rule engine, not a certified fraud detection or ML system.
 */
@Service
public class RiskEngineService {

    private final RiskRuleRepository ruleRepository;
    private final RiskAlertRepository alertRepository;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RiskEngineService(
            RiskRuleRepository ruleRepository,
            RiskAlertRepository alertRepository,
            OutboxService outboxService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.ruleRepository = ruleRepository;
        this.alertRepository = alertRepository;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @EventListener
    @Transactional
    public void onOutboxEvent(OutboxService.PublishedOutboxEvent event) {
        if (!outboxService.tryClaimInbox("RISK_ENGINE", event.eventId().toString())) {
            return;
        }
        try {
            JsonNode payload = objectMapper.readTree(event.payloadJson());
            if (event.eventType().startsWith("PAYMENT_")) {
                String paymentId = payload.get("paymentId").asText();
                UUID merchantId = UUID.fromString(payload.get("merchantId").asText());
                long amountMinor = payload.get("amountMinor").asLong();
                String status = payload.get("status").asText();
                evaluatePayment(paymentId, merchantId, amountMinor, status);
            } else if (event.eventType().startsWith("PAYOUT_")) {
                String payoutId = payload.get("payoutId").asText();
                UUID merchantId = payload.hasNonNull("merchantId")
                        ? UUID.fromString(payload.get("merchantId").asText()) : null;
                long amountMinor = payload.get("amountMinor").asLong();
                evaluatePayout(payoutId, merchantId, amountMinor);
            }
        } catch (Exception ignored) {
            // Non-critical event formats ignored safely
        }
    }

    @Transactional
    public void evaluatePayment(String paymentId, UUID merchantId, long amountMinor, String status) {
        ruleRepository.findById("HIGH_AMOUNT_TX").ifPresent(rule -> {
            if (rule.isEnabled() && amountMinor >= rule.getThresholdValue()) {
                alertRepository.save(new RiskAlertEntity(
                        UUID.randomUUID(),
                        rule.getRuleCode(),
                        "PAYMENT",
                        paymentId,
                        merchantId,
                        rule.getSeverity(),
                        rule.getScoreWeight(),
                        "Payment amount " + amountMinor + " minor units exceeded threshold " + rule.getThresholdValue(),
                        Instant.now()
                ));
            }
        });

        if ("UNKNOWN".equals(status) || "FAILED".equals(status)) {
            ruleRepository.findById("SUSPICIOUS_PROVIDER_ACTIVITY").ifPresent(rule -> {
                if (rule.isEnabled() && "UNKNOWN".equals(status)) {
                    alertRepository.save(new RiskAlertEntity(
                            UUID.randomUUID(),
                            rule.getRuleCode(),
                            "PAYMENT",
                            paymentId,
                            merchantId,
                            rule.getSeverity(),
                            rule.getScoreWeight(),
                            "Payment entered UNKNOWN state after provider timeout; requires verification",
                            Instant.now()
                    ));
                }
            });
        }
    }

    @Transactional
    public void evaluatePayout(String payoutId, UUID merchantId, long amountMinor) {
        ruleRepository.findById("NEW_BENEFICIARY_PAYOUT").ifPresent(rule -> {
            if (rule.isEnabled() && amountMinor >= rule.getThresholdValue()) {
                alertRepository.save(new RiskAlertEntity(
                        UUID.randomUUID(),
                        rule.getRuleCode(),
                        "PAYOUT",
                        payoutId,
                        merchantId,
                        rule.getSeverity(),
                        rule.getScoreWeight(),
                        "Payout amount " + amountMinor + " minor units exceeded secondary review threshold "
                                + rule.getThresholdValue(),
                        Instant.now()
                ));
            }
        });
    }

    @Transactional(readOnly = true)
    public List<RiskRuleEntity> listRules() {
        return ruleRepository.findAll();
    }

    @Transactional
    public RiskRuleEntity updateRule(String ruleCode, boolean enabled, long thresholdValue, int scoreWeight,
                                     UUID actorId, String actorEmail, String roles, String ip) {
        RiskRuleEntity rule = ruleRepository.findById(ruleCode)
                .orElseThrow(() -> new DomainException("RISK_RULE_NOT_FOUND", "Risk rule not found", HttpStatus.NOT_FOUND));
        rule.update(enabled, thresholdValue, scoreWeight);
        ruleRepository.save(rule);
        auditService.record(actorId, actorEmail, roles, "RISK_RULE_UPDATED",
                "RISK_RULE", ruleCode, AuditOutcome.SUCCESS, ip,
                "{\"enabled\":" + enabled + ",\"threshold\":" + thresholdValue + "}");
        return rule;
    }

    @Transactional(readOnly = true)
    public PagedResponse<RiskAlertEntity> listAlerts(RiskAlertEntity.AlertStatus status, int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        var p = status != null
                ? alertRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : alertRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PagedResponse.from(p);
    }

    @Transactional
    public RiskAlertEntity updateAlert(UUID alertId, RiskAlertEntity.AlertStatus nextStatus, String notes,
                                       UUID actorId, String actorEmail, String roles, String ip) {
        RiskAlertEntity alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new DomainException("RISK_ALERT_NOT_FOUND", "Risk alert not found", HttpStatus.NOT_FOUND));
        alert.updateInvestigation(nextStatus, notes, actorEmail);
        alertRepository.save(alert);
        auditService.record(actorId, actorEmail, roles, "RISK_ALERT_UPDATED",
                "RISK_ALERT", alertId.toString(), AuditOutcome.SUCCESS, ip,
                "{\"status\":\"" + nextStatus.name() + "\"}");
        return alert;
    }
}
