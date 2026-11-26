package com.payshield.payment;

import com.payshield.provider.ProviderCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {}

    public record CreatePaymentRequest(
            @NotNull UUID merchantId,
            @NotBlank String merchantOrderRef,
            @Min(1) long amountMinor,
            @NotBlank @Pattern(regexp = "^(INR|USD|EUR|GBP)$") String currency,
            ProviderCode preferredProvider
    ) {}

    public record PaymentDto(
            UUID id,
            UUID merchantId,
            String merchantOrderRef,
            long amountMinor,
            String currency,
            PaymentStatus status,
            long refundedAmountMinor,
            String selectedProvider,
            String providerReference,
            String idempotencyKey,
            String failureReason,
            boolean demoData,
            boolean idempotentReplay,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static PaymentDto from(PaymentEntity e, boolean replay) {
            return new PaymentDto(
                    e.getId(), e.getMerchantId(), e.getMerchantOrderRef(),
                    e.getAmountMinor(), e.getCurrency(), e.getStatus(),
                    e.getRefundedAmountMinor(), e.getSelectedProvider(),
                    e.getProviderReference(), e.getIdempotencyKey(),
                    e.getFailureReason(), e.isDemoData(), replay,
                    e.getCreatedAt(), e.getUpdatedAt()
            );
        }
    }

    public record PaymentAttemptDto(
            UUID id,
            int attemptNumber,
            String providerCode,
            String providerRequestId,
            String providerReference,
            String outcome,
            int latencyMs,
            String errorCode,
            String errorMessage,
            Instant createdAt
    ) {
        public static PaymentAttemptDto from(PaymentAttemptEntity a) {
            return new PaymentAttemptDto(
                    a.getId(), a.getAttemptNumber(), a.getProviderCode(),
                    a.getProviderRequestId(), a.getProviderReference(),
                    a.getOutcome(), a.getLatencyMs(), a.getErrorCode(),
                    a.getErrorMessage(), a.getCreatedAt()
            );
        }
    }

    public record PaymentEventDto(
            UUID id,
            String fromStatus,
            String toStatus,
            String eventType,
            String actor,
            String correlationId,
            String detailsJson,
            Instant createdAt
    ) {
        public static PaymentEventDto from(PaymentEventEntity e) {
            return new PaymentEventDto(
                    e.getId(), e.getFromStatus(), e.getToStatus(),
                    e.getEventType(), e.getActor(), e.getCorrelationId(),
                    e.getDetailsJson(), e.getCreatedAt()
            );
        }
    }

    public record PaymentDetailDto(
            PaymentDto payment,
            List<PaymentAttemptDto> attempts,
            List<PaymentEventDto> events
    ) {}
}
