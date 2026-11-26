package com.payshield.payment;

public enum PaymentStatus {
    CREATED,
    PROCESSING,
    UNKNOWN,
    SUCCEEDED,
    FAILED,
    RECONCILIATION_REQUIRED,
    REFUNDED,
    PARTIALLY_REFUNDED
}
