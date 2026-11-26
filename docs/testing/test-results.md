# PayShield — Verified Test & Benchmark Results

All results below were directly executed and observed in this workspace.

---

## 1. Backend Automated Test Suite (`mvn test`, `test` profile / H2 PostgreSQL mode)
- **Result**: `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0` (`BUILD SUCCESS`)
- **Test Suites**:
  - `MoneyTest` (3 tests): integer minor-unit arithmetic, positive-amount validation, currency mismatch rejection
  - `PaymentStateMachineTest` (2 tests): valid transitions & illegal terminal-to-active rejection
  - `ArchitectureRulesTest` (2 tests): ArchUnit verification that `common` and `audit` do not depend on business modules
  - `AuthAndMerchantIntegrationTest` (4 tests): login/refresh rotation/reuse revocation/logout, invalid password 401, cross-merchant isolation 403, `/api/v1/ops/health`
  - `PaymentAndWebhookIntegrationTest` (4 tests): idempotency replay & 409 payload mismatch, 5-thread concurrent idempotent creation, `TIMEOUT_AFTER_PROCESSING` -> `UNKNOWN` -> `SUCCEEDED` verification, HMAC-SHA256 webhook validation & duplicate deduplication + outbox relay
  - `LedgerAndReconciliationTest` (4 tests): unbalanced journal rejection, duplicate posting reference prevention & reversal, `FINANCIAL_AUDITOR` 403 on reversal, CSV reconciliation mismatch & duplicate file rejection
  - `PayoutAndRiskIntegrationTest` (3 tests): maker-checker separation of duties (`SEPARATION_OF_DUTIES_VIOLATION`), unauthorized execution rejection (`PAYOUT_NOT_APPROVED`), non-allowlisted beneficiary & emergency kill switch enforcement, rule-based risk alert generation & resolution

---

## 2. Frontend Build (`npm run build`)
- **Result**: `tsc -b && vite build` exited with code `0` (`dist/` built cleanly)

---

## 3. Browser End-to-End Verification (`http://localhost:5173`)
Verified against live Spring Boot (`local` profile) + Vite dev server:
- Authenticated as `admin@payshield.local` (`PLATFORM_ADMIN`)
- Loaded Operations Overview (8 live KPI cards + 3 provider status cards)
- Initiated simulated payment `ORD-78042` (`₹1,250.00` via `PROVIDER_ALPHA`) -> `SUCCEEDED` (`SIM_ALPHA_B628C65B`)
- Verified automatic double-entry ledger posting in Ledger Explorer (`DEBIT Provider Clearing ₹1,250.00`, `CREDIT Acme Retail Payable ₹1,225.00`, `CREDIT Platform Fee Revenue ₹25.00`)
- Verified `AUTH_LOGIN` and `PAYMENT_INITIATED` records in Audit Trail

---

## 4. k6 Baseline Load Test (`k6 run k6/payment-load.js`)
- **Profile**: 5 VUs for 20s against `http://localhost:8080` (`local` H2 profile)
- **Total Requests**: `396` (`19.17 req/s`)
- **Checks Succeeded**: `100.00%` (396/396)
- **Failure Rate**: `0.00%`
- **Latency**: `avg=55.84ms`, `med(p50)=51.63ms`, `p(90)=70.01ms`, `p(95)=77.47ms`, `max=386.92ms`
