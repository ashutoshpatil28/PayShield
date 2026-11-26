# PayShield — Enterprise FinTech Payment Operations & Infrastructure Platform

> **Simulation Notice**: PayShield is a portfolio & engineering simulation platform. It uses only synthetic financial data and simulated payment providers (`PROVIDER_ALPHA`, `PROVIDER_BETA`, `PROVIDER_GAMMA`). It never connects to real banks, card networks, or UPI rails.

---

## Core Capabilities

- **Authentication & RBAC**: JWT access tokens, refresh token rotation with reuse detection, BCrypt password hashing, login rate limiting, 7 least-privilege roles (`PLATFORM_ADMIN`, `MERCHANT_ADMIN`, `OPERATIONS_ANALYST`, `RISK_ANALYST`, `PAYOUT_MAKER`, `PAYOUT_CHECKER`, `FINANCIAL_AUDITOR`), and strict merchant-level isolation.
- **Payment Processing Engine**: Centralized state machine (`CREATED`, `PROCESSING`, `UNKNOWN`, `SUCCEEDED`, `FAILED`, `RECONCILIATION_REQUIRED`, `REFUNDED`, `PARTIALLY_REFUNDED`), integer minor-unit `Money` (INR paise), concurrent-safe `Idempotency-Key` enforcement with payload hash verification, and uncertainty-safe timeout handling (`UNKNOWN` requires explicit status verification before retry/failover).
- **Simulated Providers & Resilience4j**: Three independently configurable providers with failure injection (`NORMAL_SUCCESS`, `DECLINE`, `DELAYED_RESPONSE`, `TIMEOUT_AFTER_PROCESSING`, `TIMEOUT_BEFORE_PROCESSING`, `UNAVAILABLE`, `INVALID_RESPONSE`, `INTERMITTENT_ERRORS`) and Resilience4j circuit breakers.
- **Webhooks & Transactional Outbox/Inbox**: HMAC-SHA256 signature verification, +/- 300s timestamp replay protection, inbox deduplication, and atomic outbox persistence with scheduled relay.
- **Double-Entry Financial Ledger**: Immutable balanced journal transactions (`SUM(DEBIT) == SUM(CREDIT)`), unique `LedgerPostingReference` deduplication, real-time account balances, and controlled reversal transactions.
- **Settlement Reconciliation**: Synthetic CSV ingestion, SHA-256 duplicate file rejection, matching engine, exception categorization (`AMOUNT_MISMATCH`, `CURRENCY_MISMATCH`, `FEE_MISMATCH`, `DUPLICATE_TRANSACTION`, `UNKNOWN_PROVIDER_REFERENCE`, `DELAYED_SETTLEMENT`), and discrepancy investigation cases.
- **Payout Authorization & Risk Engine**: Allowlisted beneficiaries, per-tx & 24h limits, maker-checker separation of duties (`maker_user_id <> checker_user_id` enforced in service & DB constraint), emergency kill switch, and deterministic rule-based risk alerts.

---

## Quickstart (Local Development)

### Prerequisites
- Java 21 LTS & Maven 3.9+
- Node.js 20+ & npm
- Docker & Docker Compose (for PostgreSQL 16)

### 1. Start PostgreSQL
```bash
cp .env.example .env
docker compose up -d postgres
```

### 2. Run Backend (Spring Boot + Flyway)
```bash
cd backend
mvn spring-boot:run
```
- API & Health: `http://localhost:8080/api/v1/ops/health`
- OpenAPI / Swagger UI: `http://localhost:8080/swagger-ui.html`

### 3. Run Frontend (React + Vite)
```bash
cd frontend
npm install
npm run dev
```
- Operations Console: `http://localhost:5173`

### Seeded Local Demo Accounts
Password for all local demo accounts: `PayShield@Demo2026!`
- `admin@payshield.local` (`PLATFORM_ADMIN`)
- `acme.merchant@payshield.local` (`MERCHANT_ADMIN` — Acme Retail)
- `nova.merchant@payshield.local` (`MERCHANT_ADMIN` — Nova Cloud)
- `ops@payshield.local` (`OPERATIONS_ANALYST`)
- `risk@payshield.local` (`RISK_ANALYST`)
- `maker@payshield.local` (`PAYOUT_MAKER`)
- `checker@payshield.local` (`PAYOUT_CHECKER`)
- `auditor@payshield.local` (`FINANCIAL_AUDITOR`)

---

## Automated Tests

```bash
# Backend unit, integration, and ArchUnit tests
cd backend && mvn test

# Frontend TypeScript & Vite build
cd frontend && npm run build

# k6 performance test (requires running backend)
k6 run k6/payment-load.js
```

---

## Documentation
- [Implementation Plan](docs/IMPLEMENTATION_PLAN.md)
- [System Overview](docs/architecture/system-overview.md)
- [Database Design](docs/architecture/database-design.md)
- [Threat Model](docs/security/threat-model.md)
- [Security Controls](docs/security/security-controls.md)
- [Local Runbook](docs/operations/local-runbook.md)
- [Known Limitations](docs/limitations.md)
