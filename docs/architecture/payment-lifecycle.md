# PayShield — Payment & Payout Lifecycle State Machines

## 1. Payment Lifecycle State Machine

```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> PROCESSING : Provider attempt started
    CREATED --> FAILED : Pre-dispatch validation failure

    PROCESSING --> SUCCEEDED : Provider confirmed success
    PROCESSING --> FAILED : Provider decline / invalid / unavailable
    PROCESSING --> UNKNOWN : Provider timeout (never auto-fail)
    PROCESSING --> RECONCILIATION_REQUIRED : Conflicting state

    UNKNOWN --> SUCCEEDED : Explicit verify / webhook confirms success
    UNKNOWN --> FAILED : Explicit verify / webhook confirms decline or not found
    UNKNOWN --> RECONCILIATION_REQUIRED : Unresolvable mismatch

    SUCCEEDED --> PARTIALLY_REFUNDED : Partial refund
    SUCCEEDED --> REFUNDED : Full refund
    SUCCEEDED --> RECONCILIATION_REQUIRED : Settlement discrepancy

    PARTIALLY_REFUNDED --> PARTIALLY_REFUNDED : Additional partial refund
    PARTIALLY_REFUNDED --> REFUNDED : Remaining amount refunded

    RECONCILIATION_REQUIRED --> SUCCEEDED : Resolved as succeeded
    RECONCILIATION_REQUIRED --> FAILED : Resolved as failed

    FAILED --> [*]
    REFUNDED --> [*]
```

### Uncertainty Safety Rule
A provider timeout (`TIMEOUT_AFTER_PROCESSING` or `TIMEOUT_BEFORE_PROCESSING`) **must** transition the payment to `UNKNOWN` and never to `FAILED`. Automated failover to a secondary provider is prohibited while a payment is in `UNKNOWN` or `PROCESSING` without first calling `verifyStatus` on the original provider request ID.

---

## 2. Payout Lifecycle State Machine

```mermaid
stateDiagram-v2
    [*] --> REQUESTED
    REQUESTED --> VALIDATED : Allowlist & limits passed
    REQUESTED --> FAILED : Validation failed

    VALIDATED --> PENDING_MAKER_CHECKER : Queued for checker (24h expiry)
    VALIDATED --> FAILED : Pre-queue failure

    PENDING_MAKER_CHECKER --> APPROVED : Checker != Maker approves
    PENDING_MAKER_CHECKER --> REJECTED : Checker rejects

    APPROVED --> EXECUTION_PENDING : Kill switch off & not expired
    APPROVED --> REJECTED : Cancelled before execution

    EXECUTION_PENDING --> EXECUTED : Ledger posted & settled
    EXECUTION_PENDING --> UNKNOWN : Execution timeout
    EXECUTION_PENDING --> FAILED : Execution failure

    UNKNOWN --> EXECUTED : Verified executed
    UNKNOWN --> FAILED : Verified failed

    REJECTED --> [*]
    EXECUTED --> [*]
    FAILED --> [*]
```
