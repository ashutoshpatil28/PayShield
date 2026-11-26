# PayShield — Technology Compatibility Report

Verified against the local toolchain and compiled/tested dependencies in `backend/pom.xml` and `frontend/package.json`:

| Component | Pinned Version | Verification Status | Notes |
|---|---|---|---|
| **Java** | OpenJDK 21.0.6 LTS (Temurin) | Verified (`mvn test`) | Target release `21` |
| **Maven** | Apache Maven 3.9.9 | Verified (`mvn test`) | Compiles 106+ main classes |
| **Spring Boot** | `3.4.3` | Verified (`mvn test`) | Web, Security, Validation, Data JPA, Actuator, AOP |
| **PostgreSQL / Flyway** | PostgreSQL `16-alpine`, Flyway Core (`10.20.1`) | Verified DDL | Migrations use `TIMESTAMP WITH TIME ZONE` and `VARCHAR(3)` for cross-compatibility with PostgreSQL 16 and H2 2.3 `MODE=PostgreSQL` |
| **H2 (Test/Local Profile)** | `2.3.232` | Verified (`mvn test`) | In-memory PostgreSQL compatibility mode when Docker daemon is stopped |
| **JJWT** | `0.12.6` | Verified (`mvn test`) | `HS256` JWT issuance and parsing |
| **Resilience4j** | `2.3.0` (`resilience4j-spring-boot3`) | Verified (`mvn test`) | Circuit breaker per simulated provider |
| **SpringDoc OpenAPI** | `2.8.5` | Verified (`mvn test`) | `/v3/api-docs` & `/swagger-ui.html` |
| **ArchUnit** | `1.3.0` (`archunit-junit5`) | Verified (`mvn test`) | Enforces module boundary rules |
| **Node.js / npm** | Node `v20.16.0`, npm `10.8.1` | Verified (`npm run build`) | Frontend build & tooling |
| **React / Vite / TS** | React `18.3.1`, Vite `6.1.0`, TypeScript `5.7.3` | Verified (`tsc -b && vite build`) | Zero TypeScript or bundler errors |
| **Tailwind / TanStack / Recharts** | Tailwind `3.4.17`, TanStack Query `5.66.0`, Recharts `2.15.1`, Lucide `0.475.0` | Verified (`npm run build`) | Note: Recharts `2.15.x` is functional; v3 upgrade is optional future work |
| **k6** | `v2.2.0` | Verified (`k6 run`) | Executed baseline profile against live backend |
| **Docker / Compose** | Docker `29.8.2`, Compose `v5.6.0` | CLI installed | Daemon must be started to run `docker compose up -d postgres` and Testcontainers |
