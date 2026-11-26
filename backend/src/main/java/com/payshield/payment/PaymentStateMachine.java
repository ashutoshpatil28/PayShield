package com.payshield.payment;

import com.payshield.common.DomainException;
import org.springframework.http.HttpStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centralized payment state machine. Prevents invalid state transitions and enforces
 * uncertainty-safety rules (e.g., timeouts enter UNKNOWN rather than FAILED).
 */
public final class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED.put(PaymentStatus.CREATED, EnumSet.of(
                PaymentStatus.PROCESSING,
                PaymentStatus.FAILED
        ));
        ALLOWED.put(PaymentStatus.PROCESSING, EnumSet.of(
                PaymentStatus.SUCCEEDED,
                PaymentStatus.FAILED,
                PaymentStatus.UNKNOWN,
                PaymentStatus.RECONCILIATION_REQUIRED
        ));
        ALLOWED.put(PaymentStatus.UNKNOWN, EnumSet.of(
                PaymentStatus.SUCCEEDED,
                PaymentStatus.FAILED,
                PaymentStatus.RECONCILIATION_REQUIRED
        ));
        ALLOWED.put(PaymentStatus.SUCCEEDED, EnumSet.of(
                PaymentStatus.PARTIALLY_REFUNDED,
                PaymentStatus.REFUNDED,
                PaymentStatus.RECONCILIATION_REQUIRED
        ));
        ALLOWED.put(PaymentStatus.PARTIALLY_REFUNDED, EnumSet.of(
                PaymentStatus.PARTIALLY_REFUNDED,
                PaymentStatus.REFUNDED
        ));
        ALLOWED.put(PaymentStatus.RECONCILIATION_REQUIRED, EnumSet.of(
                PaymentStatus.SUCCEEDED,
                PaymentStatus.FAILED
        ));
        ALLOWED.put(PaymentStatus.FAILED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED.put(PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class));
    }

    private PaymentStateMachine() {}

    public static boolean canTransition(PaymentStatus from, PaymentStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(PaymentStatus.class)).contains(to);
    }

    public static void assertTransition(PaymentStatus from, PaymentStatus to) {
        if (!canTransition(from, to)) {
            throw new DomainException(
                    "INVALID_PAYMENT_TRANSITION",
                    "Illegal payment state transition from " + from + " to " + to,
                    HttpStatus.CONFLICT
            );
        }
    }
}
