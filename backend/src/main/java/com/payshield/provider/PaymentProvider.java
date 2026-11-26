package com.payshield.provider;

public interface PaymentProvider {

    enum Outcome {
        SUCCEEDED,
        DECLINED,
        TIMEOUT_UNKNOWN,
        UNAVAILABLE,
        INVALID_RESPONSE
    }

    enum VerificationOutcome {
        SUCCEEDED,
        DECLINED,
        NOT_FOUND,
        STILL_UNKNOWN
    }

    record ChargeCommand(
            ProviderCode providerCode,
            String providerRequestId,
            long amountMinor,
            String currency,
            String merchantOrderRef
    ) {}

    record ChargeResult(
            Outcome outcome,
            String providerReference,
            int latencyMs,
            String errorCode,
            String errorMessage
    ) {}

    record VerificationResult(
            VerificationOutcome outcome,
            String providerReference,
            String message
    ) {}

    ChargeResult charge(ChargeCommand command);

    VerificationResult verifyStatus(ProviderCode providerCode, String providerRequestId);
}
