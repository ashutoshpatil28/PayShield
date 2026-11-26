package com.payshield.provider;

import com.payshield.common.DomainException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SimulatedProviderGateway implements PaymentProvider {

    private final ProviderConfigRepository configRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    // Provider-side synthetic transaction state keyed by providerRequestId (simulates remote idempotency & post-timeout status)
    private final Map<String, ProviderTxRecord> remoteTxStore = new ConcurrentHashMap<>();
    private final Map<ProviderCode, ProviderMetrics> metricsMap = new EnumMap<>(ProviderCode.class);

    public record ProviderTxRecord(VerificationOutcome outcome, String providerReference) {}

    public static class ProviderMetrics {
        final AtomicLong totalRequests = new AtomicLong();
        final AtomicLong successCount = new AtomicLong();
        final AtomicLong failureCount = new AtomicLong();
        final AtomicLong totalLatencyMs = new AtomicLong();
        volatile Instant lastSuccessAt;

        public long getTotalRequests() { return totalRequests.get(); }
        public long getSuccessCount() { return successCount.get(); }
        public long getFailureCount() { return failureCount.get(); }
        public long getAverageLatencyMs() {
            long total = totalRequests.get();
            return total == 0 ? 0 : totalLatencyMs.get() / total;
        }
        public Instant getLastSuccessAt() { return lastSuccessAt; }
    }

    public SimulatedProviderGateway(ProviderConfigRepository configRepository) {
        this.configRepository = configRepository;
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(60)
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(cbConfig);
        for (ProviderCode code : ProviderCode.values()) {
            metricsMap.put(code, new ProviderMetrics());
        }
    }

    @Override
    public ChargeResult charge(ChargeCommand command) {
        ProviderConfigEntity cfg = configRepository.findById(command.providerCode().name())
                .orElseThrow(() -> new DomainException("PROVIDER_NOT_FOUND",
                        "Unknown provider: " + command.providerCode(), HttpStatus.BAD_REQUEST));

        ProviderMetrics metrics = metricsMap.get(command.providerCode());
        metrics.totalRequests.incrementAndGet();

        if (!cfg.isEnabled()) {
            metrics.failureCount.incrementAndGet();
            return new ChargeResult(Outcome.UNAVAILABLE, null, 0, "PROVIDER_DISABLED", "Provider is disabled");
        }

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(command.providerCode().name());
        try {
            return cb.executeSupplier(() -> executeSimulatedCharge(cfg, command, metrics));
        } catch (CallNotPermittedException ex) {
            metrics.failureCount.incrementAndGet();
            return new ChargeResult(Outcome.UNAVAILABLE, null, 0,
                    "CIRCUIT_BREAKER_OPEN", "Provider circuit breaker is OPEN");
        } catch (ProviderUnavailableSignal ex) {
            return new ChargeResult(Outcome.UNAVAILABLE, null, cfg.getLatencyMs(),
                    "PROVIDER_UNAVAILABLE", ex.getMessage());
        }
    }

    private ChargeResult executeSimulatedCharge(ProviderConfigEntity cfg,
                                                ChargeCommand command,
                                                ProviderMetrics metrics) {
        // Provider-side idempotency check
        ProviderTxRecord existing = remoteTxStore.get(command.providerRequestId());
        if (existing != null && existing.outcome() == VerificationOutcome.SUCCEEDED) {
            metrics.successCount.incrementAndGet();
            metrics.lastSuccessAt = Instant.now();
            return new ChargeResult(Outcome.SUCCEEDED, existing.providerReference(), cfg.getLatencyMs(), null, null);
        }

        int effectiveLatency = cfg.getLatencyMs();
        if (cfg.getFailureMode() == SimulatorFailureMode.DELAYED_RESPONSE) {
            effectiveLatency = Math.max(effectiveLatency, 350);
        }
        simulateDelay(effectiveLatency);
        metrics.totalLatencyMs.addAndGet(effectiveLatency);

        SimulatorFailureMode mode = resolveEffectiveMode(cfg);
        String ref = command.providerCode().name().replace("PROVIDER_", "SIM_") + "_"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        return switch (mode) {
            case NORMAL_SUCCESS, DELAYED_RESPONSE, DUPLICATE_WEBHOOK, DELAYED_WEBHOOK -> {
                remoteTxStore.put(command.providerRequestId(),
                        new ProviderTxRecord(VerificationOutcome.SUCCEEDED, ref));
                metrics.successCount.incrementAndGet();
                metrics.lastSuccessAt = Instant.now();
                yield new ChargeResult(Outcome.SUCCEEDED, ref, effectiveLatency, null, null);
            }
            case DECLINE -> {
                remoteTxStore.put(command.providerRequestId(),
                        new ProviderTxRecord(VerificationOutcome.DECLINED, null));
                metrics.failureCount.incrementAndGet();
                yield new ChargeResult(Outcome.DECLINED, null, effectiveLatency,
                        "SIM_DECLINED", "Simulated card/rail decline");
            }
            case TIMEOUT_AFTER_PROCESSING -> {
                // Processed on provider side, but response timed out before reaching PayShield!
                remoteTxStore.put(command.providerRequestId(),
                        new ProviderTxRecord(VerificationOutcome.SUCCEEDED, ref));
                metrics.failureCount.incrementAndGet();
                yield new ChargeResult(Outcome.TIMEOUT_UNKNOWN, null, effectiveLatency,
                        "READ_TIMEOUT", "Provider response timed out after request dispatch");
            }
            case TIMEOUT_BEFORE_PROCESSING -> {
                // Request never reached provider processing
                remoteTxStore.put(command.providerRequestId(),
                        new ProviderTxRecord(VerificationOutcome.NOT_FOUND, null));
                metrics.failureCount.incrementAndGet();
                yield new ChargeResult(Outcome.TIMEOUT_UNKNOWN, null, effectiveLatency,
                        "CONNECT_TIMEOUT", "Connection timed out before provider processed request");
            }
            case UNAVAILABLE -> {
                metrics.failureCount.incrementAndGet();
                throw new ProviderUnavailableSignal("Simulated provider 503 unavailable");
            }
            case INVALID_RESPONSE -> {
                metrics.failureCount.incrementAndGet();
                yield new ChargeResult(Outcome.INVALID_RESPONSE, null, effectiveLatency,
                        "MALFORMED_PAYLOAD", "Provider returned invalid response payload");
            }
            case INTERMITTENT_ERRORS -> {
                // Fallback if resolved directly
                metrics.failureCount.incrementAndGet();
                yield new ChargeResult(Outcome.DECLINED, null, effectiveLatency,
                        "INTERMITTENT_ERROR", "Intermittent simulated error");
            }
        };
    }

    @Override
    public VerificationResult verifyStatus(ProviderCode providerCode, String providerRequestId) {
        ProviderTxRecord record = remoteTxStore.get(providerRequestId);
        if (record == null) {
            return new VerificationResult(VerificationOutcome.NOT_FOUND, null,
                    "No transaction found at " + providerCode + " for request " + providerRequestId);
        }
        return new VerificationResult(record.outcome(), record.providerReference(),
                "Verified status from " + providerCode + ": " + record.outcome());
    }

    private SimulatorFailureMode resolveEffectiveMode(ProviderConfigEntity cfg) {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (cfg.getTimeoutRatePercent() > 0 && roll < cfg.getTimeoutRatePercent()) {
            return SimulatorFailureMode.TIMEOUT_AFTER_PROCESSING;
        }
        if (cfg.getFailureRatePercent() > 0 && roll < cfg.getTimeoutRatePercent() + cfg.getFailureRatePercent()) {
            return SimulatorFailureMode.DECLINE;
        }
        if (cfg.getFailureMode() == SimulatorFailureMode.INTERMITTENT_ERRORS) {
            return ThreadLocalRandom.current().nextBoolean()
                    ? SimulatorFailureMode.NORMAL_SUCCESS
                    : SimulatorFailureMode.DECLINE;
        }
        return cfg.getFailureMode();
    }

    private void simulateDelay(int ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(Math.min(ms, 2000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public List<ProviderStatusDto> listProviderStatuses() {
        return configRepository.findAll().stream()
                .sorted(Comparator.comparing(ProviderConfigEntity::getProviderCode))
                .map(cfg -> {
                    ProviderCode code = ProviderCode.valueOf(cfg.getProviderCode());
                    ProviderMetrics m = metricsMap.get(code);
                    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(cfg.getProviderCode());
                    long total = m.getTotalRequests();
                    double successRate = total == 0 ? 100.0 : (m.getSuccessCount() * 100.0) / total;
                    double errorRate = total == 0 ? 0.0 : (m.getFailureCount() * 100.0) / total;
                    return new ProviderStatusDto(
                            cfg.getProviderCode(),
                            cfg.getDisplayName(),
                            cfg.isEnabled(),
                            cfg.getFailureMode(),
                            cfg.getLatencyMs(),
                            cfg.getFailureRatePercent(),
                            cfg.getTimeoutRatePercent(),
                            cb.getState().name(),
                            total,
                            m.getSuccessCount(),
                            m.getFailureCount(),
                            Math.round(successRate * 10.0) / 10.0,
                            Math.round(errorRate * 10.0) / 10.0,
                            m.getAverageLatencyMs(),
                            m.getLastSuccessAt(),
                            cfg.getUpdatedAt()
                    );
                })
                .toList();
    }

    public void resetCircuitBreaker(ProviderCode code) {
        circuitBreakerRegistry.circuitBreaker(code.name()).reset();
    }

    public record ProviderStatusDto(
            String providerCode,
            String displayName,
            boolean enabled,
            SimulatorFailureMode failureMode,
            int latencyMs,
            int failureRatePercent,
            int timeoutRatePercent,
            String circuitBreakerState,
            long totalRequests,
            long successCount,
            long failureCount,
            double successRatePercent,
            double errorRatePercent,
            long averageLatencyMs,
            Instant lastSuccessAt,
            Instant updatedAt
    ) {}

    private static class ProviderUnavailableSignal extends RuntimeException {
        ProviderUnavailableSignal(String message) { super(message); }
    }
}
