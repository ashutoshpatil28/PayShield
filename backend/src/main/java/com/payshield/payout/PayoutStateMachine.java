package com.payshield.payout;

import com.payshield.common.DomainException;
import org.springframework.http.HttpStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class PayoutStateMachine {

    private static final Map<PayoutStatus, Set<PayoutStatus>> ALLOWED = new EnumMap<>(PayoutStatus.class);

    static {
        ALLOWED.put(PayoutStatus.REQUESTED, EnumSet.of(PayoutStatus.VALIDATED, PayoutStatus.FAILED));
        ALLOWED.put(PayoutStatus.VALIDATED, EnumSet.of(PayoutStatus.PENDING_MAKER_CHECKER, PayoutStatus.FAILED));
        ALLOWED.put(PayoutStatus.PENDING_MAKER_CHECKER, EnumSet.of(PayoutStatus.APPROVED, PayoutStatus.REJECTED));
        ALLOWED.put(PayoutStatus.APPROVED, EnumSet.of(PayoutStatus.EXECUTION_PENDING, PayoutStatus.REJECTED));
        ALLOWED.put(PayoutStatus.EXECUTION_PENDING, EnumSet.of(PayoutStatus.EXECUTED, PayoutStatus.UNKNOWN, PayoutStatus.FAILED));
        ALLOWED.put(PayoutStatus.UNKNOWN, EnumSet.of(PayoutStatus.EXECUTED, PayoutStatus.FAILED));
        ALLOWED.put(PayoutStatus.REJECTED, EnumSet.noneOf(PayoutStatus.class));
        ALLOWED.put(PayoutStatus.EXECUTED, EnumSet.noneOf(PayoutStatus.class));
        ALLOWED.put(PayoutStatus.FAILED, EnumSet.noneOf(PayoutStatus.class));
    }

    private PayoutStateMachine() {}

    public static void assertTransition(PayoutStatus from, PayoutStatus to) {
        if (!ALLOWED.getOrDefault(from, EnumSet.noneOf(PayoutStatus.class)).contains(to)) {
            throw new DomainException("INVALID_PAYOUT_TRANSITION",
                    "Illegal payout transition from " + from + " to " + to, HttpStatus.CONFLICT);
        }
    }
}
