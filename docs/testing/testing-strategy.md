# Testing strategy

Run the project commands from the repository root unless a guide specifies otherwise.

| Layer | Command | Evidence |
| --- | --- | --- |
| Java unit | `mvn -f backend/pom.xml test` | Decimal and date validation, CSV cell encoding |
| PostgreSQL and HTTP integration | `mvn -f backend/pom.xml verify` | Committed database state, SQL constraints, auth filters, concurrency and error contracts |
| Browser components | `npm --prefix frontend test` | Account display, local form validation, feedback, uncertain transfer and statement links |
| Type and lint | `npm --prefix frontend run build`, `npm --prefix frontend run lint` | Static checks and production bundle creation |
| Running services | `node scripts/smoke.mjs http://localhost:3000` | Frontend assets and live readiness through Nginx |
| Browser end to end | `npm --prefix frontend run e2e` | Sign-in, real account and transfer operations, both downloads and sign-out |

Use the [README](../../README.md#verify-the-app) to start Compose with demo funding enabled before Playwright. The integration test database is ephemeral and separate from the Compose volume. Check the [verification record](test-results.md) for observed results. Manual infrastructure exercises cover deployment-specific faults, backup restoration, load beyond the demo's scale and failures during a partially delivered download.
