# Northstar Bank implementation plan

Northstar is a local banking simulation. A React browser client talks to a single Spring Boot process over versioned HTTP endpoints, and PostgreSQL owns all durable banking state. Use relative URLs and a Vite development proxy or Nginx container proxy so browsers have a single origin. Expose minimal health information during foundation work.

## Module boundaries

| Module | Responsibility |
| --- | --- |
| `configuration` and `common` | Security configuration, request IDs, DTO validation and shared HTTP errors |
| `authentication` | Password verification, registration orchestration, session lifecycle and login throttling |
| `customers` | Customer profiles and canonical customer identity |
| `accounts` | Account creation, ownership, lifecycle and ordered account locks |
| `transfers` | Posting workflow, authorization of source, amount checks and idempotency |
| `ledger` | Append-only debit and credit records, authoritative balance and activity queries |
| `statements` | Owned-account reporting, date boundaries and file export |
| `audit` | Transactional success events and independent records of security rejections |

Controllers translate HTTP to service calls. Services enforce domain rules. Repositories own persistence queries. Transfers call explicit account, ledger and audit operations inside one database transaction. Ledger queries do not depend on the transfer service. Reporting reads committed ledger data and checks ownership through the account module. DTOs keep JPA objects out of HTTP responses.

## Initial schema

The table names below define the first schema. Milestone 2 starts the Flyway migration history with customer and credential tables. Account and posting migrations accompany their owning milestones.

| Table | Keys and integrity rules |
| --- | --- |
| `customers` | UUID primary key, unique normalized email, bounded profile fields, constrained status and creation instant |
| `credentials` | Customer UUID primary and foreign key, adaptive password hash and update instant |
| `accounts` | UUID primary key, owner foreign key for customer accounts, CAD currency, constrained type and bounded lifecycle status. Identify a system funding account separately and keep it out of customer listings. |
| `transfers` | UUID primary key, requester and account foreign keys, distinct endpoints, positive CAD amount, constrained kind and status, creation and completion instants |
| `transfer_requests` | Unique requester and UUID key pair, canonical request values or fingerprint, foreign key to the resulting transfer |
| `ledger_entries` | UUID primary key, account and transfer foreign keys, positive CAD amount and debit/credit direction, unique transfer and direction pair, index on account, posting instant and entry ID |
| `audit_events` | UUID primary key, optional customer foreign key, outcome, action, resource reference, instant and request ID |

Financial foreign keys use restrictive deletion rules. Store instants as `timestamptz` and expose UTC. Use `BigDecimal` and PostgreSQL `numeric(19,2)`. Validate scale and range before binding values so PostgreSQL never rounds invalid input silently. Customer accounts start at zero. Available balance comes from credits minus debits in the committed ledger. Account locks protect spending checks across application instances. The system funding account is the only account allowed a negative ledger balance, through an explicitly enabled and bounded demo operation.

Every posting commits one matching debit and credit, its transfer record, idempotency result and success audit together. A failed write rolls back the whole posting. A database uniqueness constraint claims each requester/key combination, and the stored request identity distinguishes a retry from a different request. Acquire account row locks in sorted UUID order, then read the latest committed balance while holding both locks. Financial records are append-only and corrections require compensating entries.

## Dependencies

- Java 21 LTS and Maven 3.9, with Spring Boot 4.0.10 managing Spring dependencies. Foundation uses Spring MVC, Security, Actuator and JDBC. Add JPA, Validation and Flyway with the first migration. [Spring Boot 4.0.10 release](https://spring.io/blog/2026/11/20/spring-boot-4-0-10-available-now/)
- PostgreSQL 17 for local Compose and PostgreSQL-backed Testcontainers tests. [PostgreSQL version policy](https://www.postgresql.org/support/versioning/)
- Node.js 24 LTS, React 19, TypeScript and Vite 8. [Node.js releases](https://nodejs.org/en/about/previous-releases) · [Vite 8](https://vite.dev/blog/announcing-vite8)
- Introduce OpenAPI via Springdoc 3, PDFBox for statements, Vitest and Testing Library for UI tests, and Playwright for full browser flows when those milestones need them. [Springdoc compatibility](https://springdoc.org/)

## Milestone order and verification

| Milestone | Verification |
| --- | --- |
| 1. Foundation | `npm ci` and `npm run build` in `frontend`, `mvn -f backend/pom.xml verify`, `docker compose --env-file .env.example config --quiet`, then a configured `docker compose up --build --wait` and the Compose smoke check |
| 2. Database and authentication | Run the Maven suite against Testcontainers and verify registration, session rotation, CSRF and logout |
| 3. Accounts | Verify account creation, zero balances, ownership and demo funding gates with real PostgreSQL |
| 4. Transaction engine | Verify committed ledger pairs, rollback, idempotency and contention with the real database |
| 5. History and statements | Verify paging and date boundaries, opening and closing balances, CSV and PDF |
| 6. Frontend completion | Run type checks, UI tests and the browser flow against the real API |
| 7. Reliability and security | Run concurrency and failure tests, verify audit writes and ensure errors expose safe information |
| 8. Testing and CI | Run the complete Maven, frontend and Playwright suites in GitHub Actions |
| 9. Portfolio documentation | Review run instructions, OpenAPI, design records, editable diagrams and recorded test results |
