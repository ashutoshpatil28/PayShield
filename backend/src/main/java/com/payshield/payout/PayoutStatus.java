package com.payshield.payout;

public enum PayoutStatus {
    REQUESTED,
    VALIDATED,
    PENDING_MAKER_CHECKER,
    APPROVED,
    REJECTED,
    EXECUTION_PENDING,
    EXECUTED,
    UNKNOWN,
    FAILED
}
