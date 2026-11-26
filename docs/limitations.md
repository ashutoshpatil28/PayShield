# PayShield — Known Limitations & Scope Boundaries

1. **Simulation Only**: All payment providers (`PROVIDER_ALPHA`, `PROVIDER_BETA`, `PROVIDER_GAMMA`), beneficiaries, and settlement CSVs are synthetic. No real banking, card, or UPI rails are connected.
2. **Delivery Guarantees**: The transactional outbox + inbox pattern provides **at-least-once** event publishing with **idempotent consumer deduplication** inside the modular monolith, not distributed exactly-once delivery across external brokers.
3. **Rule-Based Risk Engine**: The risk engine uses deterministic threshold rules for demonstration and explainability. It is **not** a certified fraud detection or machine-learning system.
4. **Local Audit Protection**: `audit_events` has no update/delete API endpoints, but in a local development database a privileged DBA with direct SQL access could still alter rows; production deployments would stream audit logs to immutable WORM storage.
5. **Docker Daemon Requirement**: `docker-compose.yml` and Testcontainers require a running Docker daemon. Unit and Spring integration tests use the `test` profile (H2 in PostgreSQL compatibility mode) when Docker is unavailable.
