# AGENTS.md — PayShield Engineering Rules & Conventions

This file defines the mandatory engineering rules, architectural boundaries, financial invariants, security requirements, and testing standards for **PayShield**. Every agent and contributor must follow these rules without exception.

---

## 1. Project Identity & Scope
- **PayShield** is an enterprise FinTech payment operations and infrastructure simulation platform.
- **Simulation Only**: Never connect to real banks, card networks, UPI rails, or external payment gateways. Use only synthetic data and simulated providers (`PROVIDER_ALPHA`, `PROVIDER_BETA`, `PROVIDER_GAMMA`).
- **No Real Sensitive Data**: Never store or log real PANs, CVVs, UPI PINs, banking passwords, or real PII.

---

## 2. Repository Structure
```text
PayShield/
├── AGENTS.md                   # Persistent engineering rules (this file)
├── README.md                   # Project overview & quickstart
├── Makefile                    # Unified dev, test, and build commands
├── docker-compose.yml          # Local PostgreSQL & supporting services
├── .env.example                # Safe environment variable template (never commit .env)
├── backend/                    # Java 21 + Spring Boot modular monolith (Maven)
├── frontend/                   # React + TypeScript + Vite + Tailwind CSS
├── k6/                         # k6 performance & resilience test scripts
└── docs/                       # Architecture, security, API, testing, ops, ADRs
```

---

## 3. Backend Architecture & Module Boundaries
The backend is a **modular monolith** under `com.payshield.*` with strict unidirectional dependencies. Circular dependencies between business modules are **forbidden**.

### Module Dependency Hierarchy
1. `common` — `Money`, `CurrencyCode`, `DomainException`, `ApiError`, `PagedResponse`, `CorrelationIdContext`. Depends on nothing.
2. `audit` — Append-only `audit_events` persistence and query. Depends only on `common`.
3. `outbox` — `outbox_events`, `inbox_events`, relay worker. Depends only on `common`.
4. `auth` — Users, roles, JWT/refresh token rotation, session revocation, RBAC, rate limiting. Depends on `common`, `audit`.
5. `merchant` — Merchants, merchant settings, tenant isolation. Depends on `common`, `auth`, `audit`.
6. `provider` — `PaymentProvider` SPI, Alpha/Beta/Gamma simulators, Resilience4j circuit breakers, failure injection config. Depends on `common`.
7. `payment` — Payment lifecycle, state machine, idempotency, attempts, event history. Depends on `common`, `merchant`, `provider`, `outbox`, `audit`.
8. `webhook` — HMAC signature validation, replay protection, inbox deduplication, payment status updates. Depends on `common`, `provider`, `payment`, `outbox`, `audit`.
9. `ledger` — Double-entry bookkeeping (`LedgerAccount`, `JournalTransaction`, `JournalLine`, `LedgerBalance`, `LedgerPostingReference`). Depends on `common`, `audit`.
10. `reconciliation` — CSV settlement ingestion, matching, discrepancy cases. Depends on `common`, `payment`, `ledger`, `audit`.
11. `payout` — Beneficiaries, allowlists, limits, maker-checker approvals, kill switch. Depends on `common`, `merchant`, `ledger`, `outbox`, `audit`.
12. `risk` — Configurable rule-based risk scoring & alert investigation. Depends on `common`, `payment`, `payout`, `audit`.
13. `ops` — Aggregated health, metrics, and overview read models. Depends on read-only interfaces of other modules.

---

## 4. Mandatory Financial Invariants
1. **Integer Minor Units Only**: Represent all monetary amounts as `long` in minor units (e.g., INR paise: ₹100.50 = `10050L`) paired with ISO-4217 currency codes. **Never use `float`, `double`, or floating-point arithmetic for money.**
2. **Centralized State Machines**:
   - Payment states: `CREATED`, `PROCESSING`, `UNKNOWN`, `SUCCEEDED`, `FAILED`, `RECONCILIATION_REQUIRED`, `REFUNDED`, `PARTIALLY_REFUNDED`.
   - Payout states: `REQUESTED`, `VALIDATED`, `PENDING_MAKER_CHECKER`, `APPROVED`, `REJECTED`, `EXECUTION_PENDING`, `EXECUTED`, `UNKNOWN`, `FAILED`.
   - Invalid transitions must throw a domain exception and be rejected.
3. **Timeout & Uncertainty Safety**:
   - A provider timeout (`UNKNOWN` outcome) must **never** be automatically classified as `FAILED`.
   - Never automatically fail over an uncertain (`UNKNOWN` / `PROCESSING`) payment to another provider without first verifying the original attempt's status with the provider.
4. **Idempotency & Deduplication**:
   - `POST /api/v1/payments` requires an `Idempotency-Key` header scoped by `(merchant_id, idempotency_key)` with request payload hash verification.
   - Webhooks must verify HMAC-SHA256 signatures, timestamp freshness, and persist `provider_events` / `inbox_events` to guarantee no duplicate financial effects.
5. **Double-Entry Ledger**:
   - Every committed `JournalTransaction` must satisfy `SUM(DEBIT) == SUM(CREDIT)` per currency, enforced in both domain logic and database validation.
   - Journal entries are **immutable**. Corrections must be posted as reversal + new journal transactions.
   - Unique `LedgerPostingReference` prevents duplicate postings for the same source event.
6. **Payout Separation of Duties**:
   - A `PAYOUT_MAKER` must **never** approve their own payout (`maker_user_id != checker_user_id` enforced in service logic and DB `CHECK` constraint).
   - Payout execution must verify an `APPROVED`, unexpired payout record and check the emergency kill switch.
7. **Reconciliation Safety**:
   - Never automatically mutate ledger entries to resolve settlement discrepancies. Discrepancies create `reconciliation_cases` for human investigation.

---

## 5. Security & Data Isolation Rules
- **Backend RBAC**: Every protected endpoint must enforce `@PreAuthorize` or explicit role checks. Frontend role checks are UI-only conveniences, never security boundaries.
- **Merchant Isolation**: `MERCHANT_ADMIN` (and merchant-scoped queries) must never read or mutate another merchant's records.
- **No Secrets in Code or Logs**: Use environment variables for JWT secrets, webhook HMAC secrets, and DB credentials. Never log passwords, tokens, or secrets.
- **Password Hashing**: Use BCrypt (`BCryptPasswordEncoder`).
- **CORS**: Explicit allowed origins only; never use `*` with credentials.
- **DTOs Only**: Never expose JPA entities directly in REST controllers.

---

## 6. Database & Migration Rules
- PostgreSQL is the primary database; Flyway migrations under `backend/src/main/resources/db/migration/` are the single source of truth.
- All timestamps must be UTC (`TIMESTAMPTZ` / `OffsetDateTime` or `Instant`).
- Critical invariants (uniqueness, foreign keys, `maker_id <> checker_id`, positive amounts, balanced journal constraints) must be backed by database constraints.

---

## 7. Testing & Verification Standards
- **No Fabricated Results**: Never report a test, build, or benchmark as passing unless it was actually executed after the final edit and observed to pass.
- **Test Pyramid**:
  - **Unit tests** (JUnit 5 + Mockito): State machines, money math, idempotency, ledger balancing, risk rules, payout maker-checker rules, reconciliation matching.
  - **Integration tests**: PostgreSQL persistence, Flyway migrations, constraints, transactional outbox, webhook deduplication, concurrent idempotency.
  - **API tests**: Auth, RBAC, merchant isolation, validation errors, end-to-end flows.
  - **Frontend & E2E tests** (Playwright): Real browser flows against running backend + frontend.
  - **Performance tests** (k6): Baseline, burst, concurrent idempotency, provider slowdown, webhook backlog.
- Keep the application runnable at the end of every milestone.
