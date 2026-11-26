# PayShield — REST API Specification

Base path: `/api/v1` | OpenAPI JSON: `/v3/api-docs` | Swagger UI: `/swagger-ui.html`

## Common Conventions
- **Authentication**: `Authorization: Bearer <accessToken>` (except `/auth/login`, `/auth/refresh`, `/webhooks/**`, `/actuator/health/**`).
- **Correlation**: Optional `X-Correlation-Id` (or `X-Request-Id`) propagated and returned in response headers and `ApiError`.
- **Standard Error Schema (`ApiError`)**: `{ timestamp, status, errorCode, message, correlationId, path, fieldErrors }`.
- **Pagination (`PagedResponse<T>`)**: `{ items, page, size, totalElements, totalPages }`.

## Endpoints

| Module | Method & Path | Roles / Auth | Purpose |
|---|---|---|---|
| **Auth** | `POST /api/v1/auth/login` | Public (rate-limited) | Authenticate & issue JWT + refresh token |
| **Auth** | `POST /api/v1/auth/refresh` | Public | Rotate refresh token (reuse revokes all) |
| **Auth** | `POST /api/v1/auth/logout` | Authenticated | Revoke active refresh tokens |
| **Auth** | `GET /api/v1/auth/me` | Authenticated | Current user profile |
| **Admin** | `GET/POST /api/v1/admin/users`, `PUT /{id}/roles`, `PATCH /{id}/status` | `PLATFORM_ADMIN` | User & RBAC management |
| **Merchants** | `GET /api/v1/merchants`, `GET /{id}` | Tenant-isolated | List/view accessible merchants |
| **Merchants** | `POST /api/v1/merchants`, `PATCH /{id}/status` | `PLATFORM_ADMIN` | Create/update merchant |
| **Payments** | `POST /api/v1/payments` (`Idempotency-Key` required) | `PLATFORM_ADMIN`, `MERCHANT_ADMIN`, `OPERATIONS_ANALYST` | Create idempotent payment |
| **Payments** | `GET /api/v1/payments`, `GET /{id}`, `GET /{id}/events` | Tenant-isolated | Search, detail & event timeline |
| **Payments** | `POST /api/v1/payments/{id}/verify` | `PLATFORM_ADMIN`, `OPERATIONS_ANALYST`, `MERCHANT_ADMIN` | Verify `UNKNOWN`/`PROCESSING` payment |
| **Providers** | `GET /api/v1/providers` | Authenticated | Provider metrics & circuit breaker state |
| **Providers** | `PUT /api/v1/providers/{code}/config`, `POST /{code}/circuit-breaker/reset` | `PLATFORM_ADMIN`, `OPERATIONS_ANALYST` | Simulator failure injection & CB reset |
| **Webhooks** | `POST /api/v1/webhooks/{providerCode}` | HMAC-SHA256 + Timestamp | Receive provider webhook |
| **Webhooks** | `GET /api/v1/webhook-events` | `PLATFORM_ADMIN`, `OPERATIONS_ANALYST`, `FINANCIAL_AUDITOR` | Inspect webhook processing |
| **Ledger** | `GET /api/v1/ledger/balances`, `GET /api/v1/ledger/transactions` | `PLATFORM_ADMIN`, `OPERATIONS_ANALYST`, `FINANCIAL_AUDITOR` | Read balances & journals |
| **Ledger** | `POST /api/v1/ledger/transactions/{txId}/reverse` | `PLATFORM_ADMIN` | Post balanced reversal |
| **Recon** | `GET /summary`, `GET/POST /files`, `GET/PATCH /cases/{id}` | `PLATFORM_ADMIN`, `OPERATIONS_ANALYST`, (`FINANCIAL_AUDITOR` read-only) | CSV reconciliation & cases |
| **Payouts** | `GET/POST /api/v1/payouts`, `POST /{id}/decision`, `POST /{id}/execute`, `GET/PUT /kill-switch` | Role- & SoD-enforced | Maker-checker payouts & kill switch |
| **Risk** | `GET/PUT /api/v1/risk/rules`, `GET/PATCH /api/v1/risk/alerts` | `PLATFORM_ADMIN`, `RISK_ANALYST`, (`OPERATIONS_ANALYST`/`AUDITOR` read) | Risk rules & alert investigation |
| **Audit & Ops** | `GET /api/v1/audit-events`, `GET /api/v1/ops/health`, `GET /api/v1/ops/overview` | Role-enforced / Authenticated | Audit trail, health & overview metrics |
