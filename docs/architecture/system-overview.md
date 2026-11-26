# PayShield — System Architecture Overview (C4, Modules & Risks)

## 1. C4 Context Architecture

```mermaid
flowchart TB
    MerchantAdmin["Merchant Admin\n(Initiates payments, views merchant-scoped data)"]
    OpsAnalyst["Operations & Risk Analysts\n(Monitors providers, reconciliation, risk alerts)"]
    PayoutTeam["Payout Maker & Checker\n(Requests & approves payouts with SoD)"]
    Auditor["Financial Auditor\n(Read-only ledger & audit trail review)"]
    PlatformAdmin["Platform Admin\n(Users, roles, kill switch, simulator config)"]

    PayShield["PayShield Platform\n(Enterprise Payment Ops & Infrastructure Simulation)"]

    SimProviders["Simulated Payment Providers\n(PROVIDER_ALPHA, PROVIDER_BETA, PROVIDER_GAMMA)\nNo real banks or card networks"]
    CsvSource["Synthetic Settlement CSVs\n(External settlement reports)"]

    MerchantAdmin --> PayShield
    OpsAnalyst --> PayShield
    PayoutTeam --> PayShield
    Auditor --> PayShield
    PlatformAdmin --> PayShield

    PayShield <-->|"Simulated Charge, Verify & Signed Webhooks"| SimProviders
    CsvSource -->|"CSV Upload"| PayShield
```

---

## 2. C4 Container Architecture

```mermaid
flowchart LR
    Browser["User Browser"] -->|"HTTPS / UI"| Frontend["Operations Console SPA\n(React 18, TypeScript, Vite, Tailwind)\nPort 5173"]
    Frontend -->|"REST/JSON + Bearer JWT\n+ Idempotency-Key"| Backend["PayShield Backend API & Workers\n(Java 21, Spring Boot 3.4.3 Modular Monolith)\nPort 8080"]
    SimWebhook["Simulated Provider Webhook\n(HMAC-SHA256 + Timestamp)"] -->|"POST /api/v1/webhooks/*"| Backend
    Backend -->|"JDBC / JPA / Flyway"| DB[("PostgreSQL 16\nACID Transactions & Constraints\nPort 5432")]
```

---

## 3. Backend Module Dependency Diagram

Strict unidirectional DAG enforced by ArchUnit (`ArchitectureRulesTest`):

```mermaid
flowchart TD
    ops --> payment
    ops --> provider
    ops --> payout
    ops --> recon["reconciliation"]
    ops --> risk
    ops --> audit

    risk --> outbox
    risk --> audit
    risk --> common

    payout --> merchant
    payout --> ledger
    payout --> outbox
    payout --> audit
    payout --> common

    recon --> payment
    recon --> audit
    recon --> common

    webhook --> provider
    webhook --> payment
    webhook --> outbox
    webhook --> audit
    webhook --> common

    payment --> merchant
    payment --> provider
    payment --> outbox
    payment --> audit
    payment --> common

    ledger --> outbox
    ledger --> audit
    ledger --> common

    merchant --> auth
    merchant --> audit
    merchant --> common

    auth --> audit
    auth --> common

    provider --> common
    outbox --> common
    audit --> common
```

---

## 4. Frontend Route Architecture

```mermaid
flowchart LR
    Login["/login\n(Public Auth)"] --> Guard{"ProtectedRoute\n(JWT Session Check)"}
    Guard --> Shell["AppShell\n(Sidebar, Header, Theme, Role Context)"]

    Shell --> Overview["/ (OverviewPage)"]
    Shell --> Payments["/payments (PaymentsPage)"]
    Shell --> PaymentDetail["/payments/:paymentId (PaymentDetailPage)"]
    Shell --> Providers["/providers (ProvidersPage)"]
    Shell --> Payouts["/payouts (PayoutsPage)"]
    Shell --> Ledger["/ledger (LedgerPage)"]
    Shell --> Recon["/reconciliation (ReconciliationPage)"]
    Shell --> Risk["/risk (RiskPage)"]
    Shell --> Health["/health (SystemHealthPage)"]
    Shell --> Audit["/audit (AuditTrailPage)"]
    Shell --> Admin["/admin (AdminPage)"]
```

---

## 5. Highest-Risk Architectural Decisions & Verification Strategy

| Risk Area | Architectural Decision | How It Will Be Tested |
|---|---|---|
| **Duplicate Payments Under Concurrency** | Scoped `UNIQUE (merchant_id, idempotency_key)` + SHA-256 request payload hash verification | Multi-threaded concurrency test (`concurrentIdempotentRequestsProduceSinglePayment`) + k6 `concurrent_idempotency` profile |
| **False Failures on Provider Timeouts** | Timeouts transition to `UNKNOWN` (never `FAILED`); requires explicit provider `verifyStatus` before resolution | Integration test with `TIMEOUT_AFTER_PROCESSING` -> `UNKNOWN` -> `/verify` -> `SUCCEEDED` |
| **Duplicate Webhook Financial Effects** | Constant-time HMAC-SHA256 + 300s replay window + `provider_events` & `inbox_events` deduplication | Integration test sending invalid signature (401), valid webhook (`PROCESSED`), and duplicate (`DUPLICATE`) |
| **Unbalanced or Mutated Ledger** | Integer minor units (`long`), `SUM(DEBIT) == SUM(CREDIT)` check, `ledger_posting_references` uniqueness, reversal-only corrections | Unit/integration tests for unbalanced rejection, duplicate posting idempotency, reversal creation, and `FINANCIAL_AUDITOR` write block (403) |
| **Payout Self-Approval & Bypass** | Service check + DB `CHECK (maker_user_id <> checker_user_id)` + `APPROVED` & kill-switch gate on execution | Integration tests verifying maker self-approval returns 403, unapproved execution returns 409, and kill switch returns 503 |
