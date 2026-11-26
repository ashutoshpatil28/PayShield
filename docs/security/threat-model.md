# PayShield — Threat Model

## 1. Assets Protected
- Double-entry financial ledger balances and journal transactions
- Payout authorization state & emergency kill switch
- Merchant-isolated payment & beneficiary records
- Authentication credentials, JWT signing keys, and refresh tokens
- Webhook HMAC-SHA256 secrets and provider event deduplication state

## 2. Key Threats & Mitigations

| Threat | Vector | Mitigation Implemented |
|---|---|---|
| **Duplicate Charge / Double Spend** | Concurrent retries of `POST /api/v1/payments` | Scoped `UNIQUE (merchant_id, idempotency_key)` + SHA-256 request payload hash verification (`409` on mismatch) |
| **Forged or Replayed Webhooks** | Spoofed `POST /api/v1/webhooks/{provider}` | Constant-time HMAC-SHA256 signature verification, +/- 300s timestamp replay window, and `provider_events` + `inbox_events` deduplication |
| **Payout Self-Approval / Insider Fraud** | `PAYOUT_MAKER` approving or executing own payout | Service-layer check + PostgreSQL `CHECK (maker_user_id <> checker_user_id)` constraint; execution requires `APPROVED` & unexpired record |
| **Cross-Tenant Data Leak** | `MERCHANT_ADMIN` querying another merchant's ID | `MerchantService.requireMerchantAccess` enforced on every merchant, payment, and payout endpoint |
| **Brute-Force Login / Token Theft** | Credential stuffing or refresh token replay | `AuthRateLimiter` sliding window, BCrypt (cost 12), hashed refresh tokens with reuse detection (revokes all user tokens on reuse) |
| **Ledger Tampering** | Direct mutation of historical journal entries | No update/delete endpoints; corrections require balanced reversal journal transactions; `FINANCIAL_AUDITOR` is strictly read-only |
