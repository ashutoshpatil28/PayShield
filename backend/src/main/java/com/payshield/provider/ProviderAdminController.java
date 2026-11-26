package com.payshield.provider;

import com.payshield.common.DomainException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/providers")
public class ProviderAdminController {

    private final ProviderConfigRepository configRepository;
    private final SimulatedProviderGateway gateway;

    public ProviderAdminController(ProviderConfigRepository configRepository,
                                   SimulatedProviderGateway gateway) {
        this.configRepository = configRepository;
        this.gateway = gateway;
    }

    public record UpdateProviderConfigRequest(
            boolean enabled,
            @NotNull SimulatorFailureMode failureMode,
            @Min(0) @Max(2000) int latencyMs,
            @Min(0) @Max(100) int failureRatePercent,
            @Min(0) @Max(100) int timeoutRatePercent
    ) {}

    @GetMapping
    public List<SimulatedProviderGateway.ProviderStatusDto> list() {
        return gateway.listProviderStatuses();
    }

    @PutMapping("/{providerCode}/config")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATIONS_ANALYST')")
    @Transactional
    public SimulatedProviderGateway.ProviderStatusDto updateConfig(
            @PathVariable ProviderCode providerCode,
            @Valid @RequestBody UpdateProviderConfigRequest req
    ) {
        ProviderConfigEntity entity = configRepository.findById(providerCode.name())
                .orElseThrow(() -> new DomainException("PROVIDER_NOT_FOUND",
                        "Unknown provider: " + providerCode, HttpStatus.NOT_FOUND));
        entity.updateConfig(req.enabled(), req.failureMode(), req.latencyMs(),
                req.failureRatePercent(), req.timeoutRatePercent());
        configRepository.save(entity);
        return gateway.listProviderStatuses().stream()
                .filter(p -> p.providerCode().equals(providerCode.name()))
                .findFirst()
                .orElseThrow();
    }

    @PostMapping("/{providerCode}/circuit-breaker/reset")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','OPERATIONS_ANALYST')")
    public SimulatedProviderGateway.ProviderStatusDto resetCircuitBreaker(@PathVariable ProviderCode providerCode) {
        gateway.resetCircuitBreaker(providerCode);
        return gateway.listProviderStatuses().stream()
                .filter(p -> p.providerCode().equals(providerCode.name()))
                .findFirst()
                .orElseThrow();
    }
}
