# PayShield — Local Operations Runbook

## Starting the Platform
1. Copy `.env.example` to `.env`.
2. Start PostgreSQL: `docker compose up -d postgres`.
3. Start Backend: `cd backend && mvn spring-boot:run`.
4. Start Frontend: `cd frontend && npm run dev`.

## Verifying Health & Readiness
- `GET http://localhost:8080/api/v1/ops/health` (authenticated)
- `GET http://localhost:8080/actuator/health/readiness` (public probe)

## Emergency Procedures (Simulation)
- **Halt All Payouts**: Toggle the Payout Kill Switch in the Payouts page or `PUT /api/v1/payouts/kill-switch` (`{"enabled":true,"reason":"Incident"}`).
- **Provider Degradation**: Open the Providers page to inspect circuit breaker states (`OPEN`, `HALF_OPEN`, `CLOSED`), adjust failure modes, or reset a tripped circuit breaker.
- **Unresolved `UNKNOWN` Payments**: Never mark `UNKNOWN` payments as `FAILED` manually; use `Verify Status with Provider` on the Payment Detail page to query the provider's remote state.
