package com.payshield.ops;

import com.payshield.outbox.OutboxService;
import com.payshield.payment.PaymentRepository;
import com.payshield.payment.PaymentStatus;
import com.payshield.payout.PayoutRequestRepository;
import com.payshield.payout.PayoutStatus;
import com.payshield.provider.SimulatedProviderGateway;
import com.payshield.reconciliation.ReconciliationCaseEntity;
import com.payshield.reconciliation.ReconciliationCaseRepository;
import com.payshield.risk.RiskAlertEntity;
import com.payshield.risk.RiskAlertRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ops")
public class OpsOverviewController {

    private final PaymentRepository paymentRepository;
    private final PayoutRequestRepository payoutRepository;
    private final ReconciliationCaseRepository reconCaseRepository;
    private final RiskAlertRepository riskAlertRepository;
    private final OutboxService outboxService;
    private final SimulatedProviderGateway providerGateway;

    public OpsOverviewController(
            PaymentRepository paymentRepository,
            PayoutRequestRepository payoutRepository,
            ReconciliationCaseRepository reconCaseRepository,
            RiskAlertRepository riskAlertRepository,
            OutboxService outboxService,
            SimulatedProviderGateway providerGateway
    ) {
        this.paymentRepository = paymentRepository;
        this.payoutRepository = payoutRepository;
        this.reconCaseRepository = reconCaseRepository;
        this.riskAlertRepository = riskAlertRepository;
        this.outboxService = outboxService;
        this.providerGateway = providerGateway;
    }

    public record OverviewMetricsDto(
            long totalPayments,
            long succeededPayments,
            long failedPayments,
            long unknownPayments,
            long pendingPayoutApprovals,
            long openReconciliationCases,
            long openRiskAlerts,
            long outboxPendingCount,
            long outboxFailedCount,
            List<SimulatedProviderGateway.ProviderStatusDto> providers
    ) {}

    @GetMapping("/overview")
    public OverviewMetricsDto overview() {
        return new OverviewMetricsDto(
                paymentRepository.count(),
                paymentRepository.countByStatus(PaymentStatus.SUCCEEDED),
                paymentRepository.countByStatus(PaymentStatus.FAILED),
                paymentRepository.countByStatus(PaymentStatus.UNKNOWN),
                payoutRepository.countByStatus(PayoutStatus.PENDING_MAKER_CHECKER),
                reconCaseRepository.countByStatus(ReconciliationCaseEntity.CaseStatus.OPEN)
                        + reconCaseRepository.countByStatus(ReconciliationCaseEntity.CaseStatus.INVESTIGATING),
                riskAlertRepository.countByStatus(RiskAlertEntity.AlertStatus.OPEN),
                outboxService.countPending(),
                outboxService.countFailed(),
                providerGateway.listProviderStatuses()
        );
    }
}
