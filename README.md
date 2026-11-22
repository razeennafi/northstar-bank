# Northstar Bank

Northstar Bank is an educational digital banking simulation for a Java and full-stack engineering portfolio. It demonstrates the engineering choices behind a balance that remains correct when requests overlap or fail. Create a demo profile, open CAD chequing and savings accounts, transfer simulated funds, inspect account activity, and download CSV or PDF statements.

| Layer | Implementation |
| --- | --- |
| Browser | React 19, TypeScript 6 and Vite 8 |
| Service | Java 21, Maven, Spring Boot 4, Spring Security and Actuator |
| Storage | PostgreSQL 17, Flyway migrations, Hibernate for profiles and accounts, and JDBC for posting and reports |
| Runtime | Docker Compose, Nginx reverse proxy and a persistent PostgreSQL volume |
| Verification | JUnit, Testcontainers, MockMvc, Vitest, React Testing Library and Playwright |

## Architecture

The backend is a modular monolith. Authentication, profiles, accounts, transfers, ledger, statements and audit have defined responsibilities in one deployable Java process. The posting workflow can update the ledger, record its idempotency key and persist its audit event within one PostgreSQL transaction. React reads balances and decisions from the API. [System and module diagrams](docs/architecture/system.md) · [Implementation plan](docs/architecture/implementation-plan.md)

The ledger is the balance source of truth. Every posting has a debit and a credit of the same amount, enforced at commit. The service locks the two account rows in UUID order before it checks available funds. Runtime failures roll back the whole transaction, including the idempotency claim. The browser can check an uncertain outcome by key or retry with the same key and reviewed details. [Schema and invariants](docs/database/schema.md) · [Transfer sequence diagrams](docs/uml/transfers.md)

Spring Security validates a server-managed session and a CSRF token on every mutation. Passwords use salted PBKDF2. Ownership comes from the authenticated session and database. [Security and audit operations](docs/security/security.md)

## Run with Docker Compose

Install Docker Desktop or Docker Engine with the Compose plugin. Copy the environment example to an ignored local file and set `POSTGRES_PASSWORD` to a unique password for this local database. Set `BANK_DEMO_FUNDING_ENABLED=true` in that file to opt into one-time simulated grants.

```powershell
Copy-Item .env.example .env
# Fill in POSTGRES_PASSWORD in .env before starting Compose.
docker compose config --quiet
docker compose up --build --wait
node scripts/smoke.mjs http://localhost:3000
```

Open the local sign-in page. If you change `FRONTEND_PORT`, use that port in your browser and smoke check. The stack binds the frontend to loopback. PostgreSQL and Spring Boot communicate on the internal Compose network. [Northstar Bank](http://localhost:3000)

| Task | Command |
| --- | --- |
| Build images | `docker compose build` |
| Follow service logs | `docker compose logs -f frontend backend db` |
| Rebuild the backend | `docker compose up -d --build --wait backend` |
| Stop services and keep database data | `docker compose down` |
| Explicitly erase the local database volume | `docker compose down --volumes` |

Images package software and dependencies. Containers are running instances of those images. The named database volume survives routine restarts and container recreation. The project network resolves the names `backend` and `db` to their containers. Inside a container, `localhost` refers to that container itself. Changing the password in `.env` after initialization does not change the existing PostgreSQL role password. Use the password already set for that volume or rotate it in PostgreSQL.

## Local development

Use Java 21, Maven 3.9 and Node.js 24 or 26. Start the database alone with a loopback port for your host backend.

```powershell
docker compose -f compose.yaml -f compose.dev.yaml up -d --wait db
```

From the repository root, start Spring Boot with the same database password used in `.env`. The defaults for the database and username are both `northstar`. Set `SPRING_DATASOURCE_URL` and `SPRING_DATASOURCE_USERNAME` if you choose different values.

```powershell
$env:SPRING_DATASOURCE_PASSWORD = Read-Host -MaskInput 'Local PostgreSQL password from .env'
$env:BANK_SECURE_COOKIES = 'false'
$env:BANK_DEMO_FUNDING_ENABLED = 'true'
mvn -f backend/pom.xml spring-boot:run
```

This example enables local HTTP sessions and simulated grants. The application defaults to secure session cookies for HTTPS.

In a separate terminal, start Vite with hot reload. It proxies readiness and API requests to the backend at `http://127.0.0.1:8080`. Set `BACKEND_URL` to change that target.

```powershell
Set-Location frontend
npm ci
npm run dev
```

Open the URL printed by Vite. Changes to React and CSS reload through Vite while requests use the live backend.

## Verify the app

```powershell
mvn -f backend/pom.xml verify
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend test
npm --prefix frontend run build
docker compose config --quiet
docker compose up --build --wait
node scripts/smoke.mjs http://localhost:3000
npm --prefix frontend exec -- playwright install chromium
npm --prefix frontend run e2e
```

`GET /actuator/health/readiness` reports `{"status":"UP"}` with HTTP 200 when the application can connect to PostgreSQL and is ready. The health payload contains only its aggregate status. The [verification record](docs/testing/test-results.md) records checks actually executed.

Playwright expects demo funding and defaults to `http://localhost:3000`. Set `NORTHSTAR_BASE_URL` if you choose a different port.

## API and portfolio demo

The API guide lists routes, request shapes, response statuses and the generated OpenAPI endpoint. [HTTP API](docs/api.md)

Capture these steps in screenshots or a short recording.

1. Register with an `example.test` email and a password made for the simulation.
2. Open chequing and savings accounts. Add demo funds to chequing.
3. Review and confirm a transfer of CAD 125.50 to savings. The two balances show CAD 374.50 and CAD 125.50.
4. Open the chequing transaction history, then generate and download the current UTC month's statement.
5. Explain the PostgreSQL row locks, rollback and idempotency using the transfer sequence diagrams.

Future work includes reconciling against external feeds in a separate simulation, tamper-evident audit storage, server-driven account lifecycle controls, shared sessions for multiple backend replicas, and performance tests with a larger synthetic ledger. The current runtime is a single-instance local simulation with in-process sessions and a bounded statement workload. See the [learning guide](docs/learning-guide/README.md) and [architecture decisions](docs/architecture/decisions.md).
