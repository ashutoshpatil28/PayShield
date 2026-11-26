# PayShield — Verified Test & Benchmark Results

All results below were directly executed and observed in this workspace.

---

## 1. Backend Automated Test Suite (`mvn test`, `test` profile)
- **Result**: `Tests run: 30, Failures: 0, Errors: 0, Skipped: 0` (`BUILD SUCCESS`)
- **Test Suites**:
  - `MoneyTest` (3/3): integer minor-unit arithmetic, positive-amount validation, currency mismatch rejection
  - `PaymentStateMachineTest` (2/2): valid transitions & illegal terminal-to-active rejection
  - `ArchitectureRulesTest` (2/2): ArchUnit verification that `common` and `audit` do not depend on business modules
  - `AuthAndMerchantIntegrationTest` (4/4): login/refresh rotation/reuse revocation/logout, invalid password `401`, cross-merchant isolation `403`, `/api/v1/ops/health`
  - `PaymentAndWebhookIntegrationTest` (7/7): idempotency replay & `409` payload mismatch, 5-thread concurrent idempotent creation, `TIMEOUT_AFTER_PROCESSING` -> `UNKNOWN` -> `/verify` -> `SUCCEEDED`, `TIMEOUT_BEFORE_PROCESSING` -> `UNKNOWN` -> `/verify` -> `FAILED`, HMAC-SHA256 webhook validation & duplicate deduplication, out-of-order callback handling + single-financial-effect ledger guarantee, Resilience4j circuit breaker trip + recovery
  - `LedgerAndReconciliationTest` (4/4): unbalanced journal rejection, duplicate posting reference prevention & reversal, `FINANCIAL_AUDITOR` `403` on reversal, CSV reconciliation mismatch & duplicate file rejection without altering ledger balances, `GET /api/v1/ledger/summary`
  - `PayoutAndRiskIntegrationTest` (3/3): maker-checker separation of duties (`SEPARATION_OF_DUTIES_VIOLATION`), unauthorized execution rejection (`PAYOUT_NOT_APPROVED`), non-allowlisted beneficiary & emergency kill switch enforcement, rule-based risk alert generation & resolution
  - `SecurityHardeningIntegrationTest` (5/5): stolen provider credential bypass rejection (`401`/`409`), IDOR cross-merchant access rejection (`403`), privilege escalation rejection (`403`), webhook replay timestamp expiration (`401`), admin credential/session revocation (`POST /api/v1/admin/users/{id}/revoke-sessions`)

---

## 2. Frontend Unit Tests, Build & Playwright E2E
- **Vitest (`npm test`)**: `4 passed` (`src/lib/format.test.ts`, 1 passed file)
- **TypeScript & Vite Build (`npm run build`)**: `tsc -b && vite build` exited with code `0`
- **Playwright E2E (`npx playwright test`)** against PostgreSQL 16 + Spring Boot (`8081`) + Vite (`5173`): `1 passed (5.1s)` — login, Operations Overview, Payments initiation (`SUCCEEDED`), Ledger Explorer, and Audit Trail.

---

## 3. k6 Load & Resilience Benchmarks (PostgreSQL 16 + Spring Boot `8081`)
- **`baseline`** (`5 VUs`, `20s`): `306` requests (`14.42 req/s`), `100.00%` checks passed (`306/306`), `0.00%` failures, `p(50)=121.25ms`, `p(95)=203.00ms`
- **`burst`** (ramp up to `25 VUs`, `30s`): `1,661` requests (`54.36 req/s`), `100.00%` checks passed (`1661/1661`), `0.00%` failures, `p(50)=111.95ms`, `p(95)=169.48ms`
- **`concurrent_idempotency`** (`10 VUs`, `30` shared iterations on same `Idempotency-Key` after `IdempotencyLockService` `REQUIRES_NEW` fix): `31` requests (`15.00 req/s`), `100.00%` checks passed (`31/31`), `0.00%` failures, `p(50)=59.83ms`, `p(95)=531.55ms` (crossed `p(95)<500ms` threshold by `31.55ms` on cold wave due to BCrypt login + DB lock contention, `0` errors)
