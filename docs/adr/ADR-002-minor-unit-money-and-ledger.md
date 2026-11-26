# ADR-002: Integer Minor-Unit Money and Immutable Double-Entry Ledger

## Status
Accepted

## Context
Floating-point types (`float`, `double`) introduce rounding errors in financial calculations, and mutable balance updates obscure audit trails.

## Decision
1. Represent all monetary values as `long` / `BIGINT` in minor units (e.g., INR paise) via `com.payshield.common.Money`.
2. Persist all financial movements as immutable `journal_transactions` + `journal_lines` where `SUM(DEBIT) == SUM(CREDIT)` per currency, keyed by `UNIQUE (source_type, source_id, posting_type)` in `ledger_posting_references`.
3. Corrections must be posted as balanced reversal transactions (`reversal_of_tx_id`).

## Consequences
- **Positive**: Exact arithmetic, full auditability, and duplicate-posting protection.
- **Trade-off**: Callers must convert display currency to minor units at UI/API boundaries.
