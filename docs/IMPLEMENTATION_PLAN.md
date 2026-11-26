# PayShield — Implementation Plan

This document tracks the milestone-by-milestone engineering plan for **PayShield**.

## Milestones

### Milestone 1: Workspace Inspection, Architecture, Schema & Engineering Rules
- Inspect local environment (Java 21, Maven 3.9.9, Node 20, npm, Docker, k6).
- Author `AGENTS.md` with module boundaries, financial invariants, security, and testing rules.
- Define system architecture and module dependency diagrams (`docs/architecture/system-overview.md`).
- Define PostgreSQL schema and financial invariants (`docs/architecture/database-design.md`).
- Identify first vertical slice: Local infra + Spring Boot skeleton + Flyway V1 + Auth/RBAC + Merchants + Health + React Login/Shell.

### Milestone 2: Backend Foundation, Database, Auth & Merchant Management (First Vertical Slice)
- Create `docker-compose.yml`, `.env.example`, `Makefile`.
- Bootstrap `backend/` Maven project (Spring Boot 3.4, Java 21, Flyway, Spring Security, JPA, Validation, OpenAPI, Actuator/OpenTelemetry).
- Implement `V1__core_auth_merchants_audit.sql` with users, roles, refresh tokens, merchants, audit_events, and seeded demo accounts.
- Implement `common`, `audit`, `auth` (JWT + refresh token rotation + logout revocation + rate limiting), `merchant` (with tenant isolation), and `ops` health endpoint.
- Bootstrap `frontend/` (Vite + React + TypeScript + Tailwind CSS + React Router + TanStack Query) with Login page and authenticated Operations Console shell.
- Verify backend tests, startup, and frontend-to-backend login flow.

### Milestone 3: Payment Engine, State Machine, Provider Simulator & Idempotency
- Flyway `V2__payments_and_providers.sql` (`payments`, `payment_attempts`, `payment_events`, `idempotency_records`, `provider_configs`).
- Implement `provider` module: `PaymentProvider` interface, Alpha/Beta/Gamma simulators with configurable failure modes, Resilience4j circuit breaker & time limiter, and admin simulator controls.
- Implement `payment` module: centralized state machine, `POST/GET /api/v1/payments`, `GET /api/v1/payments/{id}/events`, concurrent-safe idempotency, timeout -> `UNKNOWN` handling, and status verification.
- Unit & concurrency integration tests for payment state machine, idempotency, and provider timeouts.

### Milestone 4: Webhooks, Transactional Outbox & Inbox
- Flyway `V3__webhooks_outbox_inbox.sql` (`provider_events`, `outbox_events`, `inbox_events`).
- Implement `outbox` module: atomic outbox persistence, scheduled relay worker, retry & failure tracking.
- Implement `webhook` module: HMAC-SHA256 validation, timestamp replay check, inbox deduplication, out-of-order & retryable processing.
- Integration tests for duplicate webhooks, invalid signatures, and outbox delivery.

### Milestone 5: Double-Entry Ledger & Settlement Reconciliation
- Flyway `V4__ledger_and_reconciliation.sql` (`ledger_accounts`, `journal_transactions`, `journal_lines`, `ledger_balances`, `ledger_posting_references`, `settlement_files`, `settlement_records`, `reconciliation_cases`).
- Implement `ledger` module: atomic double-entry posting, `SUM(DEBIT) == SUM(CREDIT)` invariant, immutability, reversal entries, account balances.
- Implement `reconciliation` module: synthetic CSV upload, SHA-256 deduplication, matching engine, exception categorization, investigation cases.
- Unit & integration tests for all ledger and reconciliation invariants.

### Milestone 6: Payout Authorization & Risk Engine
- Flyway `V5__payouts_and_risk.sql` (`beneficiaries`, `payout_requests`, `payout_approvals`, `payout_kill_switch`, `risk_rules`, `risk_alerts`).
- Implement `payout` module: beneficiaries, allowlists, per-tx/daily/velocity limits, maker-checker workflow, separation of duties, approval expiration, kill switch.
- Implement `risk` module: configurable rule-based engine, explainable risk alerts, analyst investigation/acknowledgement/resolution.
- Unit & API tests for unauthorized payout rejection, maker-checker separation, and risk alert generation.

### Milestone 7: Full React Operations Console & API Integration
- Build all 10+ enterprise UI pages (Overview, Payments, Payment Details, Providers, Payouts & Approvals, Ledger Explorer, Reconciliation, Risk Center, System Health, Audit Trail, Administration & Settings).
- Dark/light theme support, accessible data tables, Recharts visualizations, loading/empty/error states, confirmation dialogs, visible demo-data badges.

### Milestone 8: Security Hardening & Audit Completeness
- Verify RBAC across all endpoints, merchant isolation, rate limiting, CORS, input validation, and audit coverage for every privileged action.

### Milestone 9: Automated Testing, k6 Performance & Failure Simulations
- Complete JUnit/Integration/API test suite.
- Playwright E2E suite verifying real browser workflows.
- k6 load scripts (baseline, burst, concurrent idempotency, provider slowdown, webhook backlog) and actual measured test reports.

### Milestone 10: Documentation, Visual Verification & Final Review
- Complete all architecture, security, API, testing, runbook, ADR, and limitations documentation.
- Final end-to-end verification and clean report.
