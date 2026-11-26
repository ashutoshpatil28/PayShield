# PayShield — Security Controls

1. **Authentication**: Stateless JWT access tokens (`HS256`, 15m TTL) + opaque refresh tokens stored as SHA-256 hashes with single-use rotation and reuse detection.
2. **Password Storage**: `BCryptPasswordEncoder(12)`. Passwords and tokens are never logged.
3. **RBAC**: Method-level `@PreAuthorize` across all protected controllers for 7 roles (`PLATFORM_ADMIN`, `MERCHANT_ADMIN`, `OPERATIONS_ANALYST`, `RISK_ANALYST`, `PAYOUT_MAKER`, `PAYOUT_CHECKER`, `FINANCIAL_AUDITOR`).
4. **CORS**: Explicit allowed origin list from `PAYSHIELD_CORS_ALLOWED_ORIGINS` (no `*` wildcard with credentials).
5. **Input Validation & DTOs**: Jakarta Bean Validation (`@Valid`) on all request DTOs; JPA entities are not exposed directly in auth/payment/payout/ledger controllers.
6. **Audit Trail**: Append-only `audit_events` table capturing actor, roles, action, resource, outcome, IP address, and `X-Correlation-Id`.
