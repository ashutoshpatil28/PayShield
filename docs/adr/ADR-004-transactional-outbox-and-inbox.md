# ADR-004: Transactional Outbox and Inbox Deduplication

## Status
Accepted

## Context
Critical payment and payout state transitions trigger downstream ledger postings and risk evaluations, while external webhooks may arrive multiple times or out of order.

## Decision
1. Persist `outbox_events` in the same ACID database transaction as the business state change, relayed asynchronously by `OutboxService.relayPendingEvents()`.
2. Consumers (`LEDGER_POSTER`, `RISK_ENGINE`, `WEBHOOK_PROCESSOR`) claim an idempotency key in `inbox_events` (`UNIQUE (consumer_name, event_key)`) before applying side effects.

## Consequences
- **Positive**: Guarantees **at-least-once** internal publishing with **idempotent consumer deduplication** without distributed transactions.
- **Trade-off**: Does not claim distributed exactly-once delivery across external brokers.
