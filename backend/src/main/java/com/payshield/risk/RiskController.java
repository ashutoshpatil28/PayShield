package com.payshield.risk;

import com.payshield.auth.PayShieldUserPrincipal;
import com.payshield.common.PagedResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/risk")
public class RiskController {

    private final RiskEngineService riskEngineService;

    public RiskController(RiskEngineService riskEngineService) {
        this.riskEngineService = riskEngineService;
    }

    public record UpdateRuleRequest(
            boolean enabled,
            @Min(1) long thresholdValue,
            @Min(1) @Max(100) int scoreWeight
    ) {}

    public record UpdateAlertRequest(
            @NotNull RiskAlertEntity.AlertStatus status,
            String analystNotes
    ) {}

    @GetMapping("/rules")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','RISK_ANALYST','OPERATIONS_ANALYST')")
    public List<RiskRuleEntity> listRules() {
        return riskEngineService.listRules();
    }

    @PutMapping("/rules/{ruleCode}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','RISK_ANALYST')")
    public RiskRuleEntity updateRule(
            @PathVariable String ruleCode,
            @Valid @RequestBody UpdateRuleRequest req,
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            HttpServletRequest httpReq
    ) {
        return riskEngineService.updateRule(
                ruleCode, req.enabled(), req.thresholdValue(), req.scoreWeight(),
                actor.getUserId(), actor.getEmail(), String.join(",", actor.getRoles()),
                httpReq.getRemoteAddr()
        );
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','RISK_ANALYST','OPERATIONS_ANALYST','FINANCIAL_AUDITOR')")
    public PagedResponse<RiskAlertEntity> listAlerts(
            @RequestParam(required = false) RiskAlertEntity.AlertStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return riskEngineService.listAlerts(status, page, size);
    }

    @PatchMapping("/alerts/{alertId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','RISK_ANALYST')")
    public RiskAlertEntity updateAlert(
            @PathVariable UUID alertId,
            @Valid @RequestBody UpdateAlertRequest req,
            @AuthenticationPrincipal PayShieldUserPrincipal actor,
            HttpServletRequest httpReq
    ) {
        return riskEngineService.updateAlert(
                alertId, req.status(), req.analystNotes(),
                actor.getUserId(), actor.getEmail(), String.join(",", actor.getRoles()),
                httpReq.getRemoteAddr()
        );
    }
}
