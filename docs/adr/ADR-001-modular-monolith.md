# ADR-001: Modular Monolith over Microservices

## Status
Accepted

## Context
PayShield requires strict ACID guarantees across state changes, outbox event persistence, idempotency locks, and double-entry ledger postings.

## Decision
Build PayShield as a Spring Boot **modular monolith** with explicit package boundaries (`common`, `audit`, `outbox`, `auth`, `merchant`, `provider`, `payment`, `webhook`, `ledger`, `reconciliation`, `payout`, `risk`, `ops`) enforced by ArchUnit (`ArchitectureRulesTest`) and backed by a single PostgreSQL database.

## Consequences
- **Positive**: Single-transaction atomicity for business state + outbox events; zero distributed 2PC complexity; straightforward local startup.
- **Trade-off**: Modules share one JVM process and database instance; future extraction into services would require replacing in-process Spring `ApplicationEventPublisher` with an external broker.
