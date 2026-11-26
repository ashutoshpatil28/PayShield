# ADR-003: Uncertainty-Safe Provider Timeout Handling

## Status
Accepted

## Context
When a payment provider call times out, the provider may have already captured funds (`TIMEOUT_AFTER_PROCESSING`) or never received the request (`TIMEOUT_BEFORE_PROCESSING`).

## Decision
Timeouts transition the payment to `UNKNOWN` (never `FAILED`). Automated failover to another provider is prohibited while in `UNKNOWN` or `PROCESSING`; operators or workflows must invoke explicit status verification (`verifyStatus`) against the original `provider_request_id` first.

## Consequences
- **Positive**: Eliminates double-charging customers on slow or ambiguous provider responses.
- **Trade-off**: Payments in `UNKNOWN` remain pending until resolved by `/verify` or an authenticated webhook.
