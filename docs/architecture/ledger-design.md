# PayShield — Financial Transaction Invariants & Ledger Design

1. **Integer Minor Units Only**: All monetary values are represented as `long` / `BIGINT` in minor units (e.g., INR paise: ₹100.50 = `10050L`) paired with ISO-4217 currency codes (`INR`, `USD`, `EUR`, `GBP`). Floating-point types (`float`, `double`) are strictly forbidden for money.
2. **Double-Entry Balance Invariant**: Every committed `JournalTransaction` must satisfy `SUM(DEBIT) == SUM(CREDIT)` within a single currency, enforced in `LedgerService.postJournal` before persistence.
3. **Journal Immutability & Reversals**: Historical `journal_transactions` and `journal_lines` are immutable. Corrections must be posted as a new balanced reversal transaction (`reversal_of_tx_id`) rather than updating or deleting existing rows.
4. **Duplicate Posting Prevention**: `ledger_posting_references` enforces `UNIQUE (source_type, source_id, posting_type)` so retried outbox events cannot double-post to the ledger.
5. **Idempotent Payment Creation**: `POST /api/v1/payments` requires `Idempotency-Key` scoped by `(merchant_id, idempotency_key)` with SHA-256 request payload hash verification (`409` on payload mismatch).
6. **Payout Separation of Duties**: `maker_user_id <> checker_user_id` is enforced in both `PayoutService.decide` and PostgreSQL `CHECK` constraint `chk_maker_not_checker`. Execution requires `APPROVED`, unexpired approval, and an inactive emergency kill switch.
7. **Reconciliation Non-Mutation**: Settlement discrepancies create `reconciliation_cases` for human investigation and never automatically mutate ledger entries.
