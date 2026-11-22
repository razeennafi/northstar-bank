# Verification record

## Milestone 1

The smoke check requires three healthy containers and verifies the frontend entry point, JavaScript bundle and database-backed readiness through the frontend proxy. [Run instructions](../../README.md#verify-the-app)

Verified November 22, 2026 on Windows with Java 21, Maven 3.9.12, Node.js 26.13.0 and Docker Desktop.

| Check | Result |
| --- | --- |
| Maven `verify` | Packaged the Java 21 application. No Java test sources were present in this milestone. |
| Frontend build | TypeScript and Vite succeeded on the host and in the Node.js 24 container. |
| Vitest | Four readiness tests passed. |
| Compose configuration | Base and development configurations passed validation. |
| Compose startup and smoke check | All three services were healthy. The frontend served its JavaScript bundle, proxied the database-backed readiness request and hid other Actuator routes. |

## Milestone 2

Maven `verify` passed all three authentication tests against a fresh PostgreSQL 17.12 Testcontainers database. These exercised Flyway, Hibernate validation, registration, duplicate email checks, password hashing, invalid JSON, login, session rotation, invalid CSRF, logout and rejection auditing. The React build and four readiness tests also passed after adding the API proxy.

## Milestone 3

Maven `verify` passed five integration tests including two new PostgreSQL account scenarios. The tests verified zero opening balances, ownership, the fixed grant, duplicate grant protection, balanced entries, foreign keys and append-only financial history.

## Milestone 4

Maven verification passed seven unit cases and ten PostgreSQL integration cases. The new cases verified amount scale and range, committed ledger pairs, rejected-transfer audits, idempotent replay, payload mismatch, exact-once concurrent retries, opposite-direction posting, overspending prevention and a failed credit followed by safe retry of the same key.

## Milestone 5

Maven verification passed nine unit cases and eleven integration cases. The reporting cases covered date filtering, pagination, owner isolation, statement boundaries, totals calculated by PostgreSQL, escaped CSV cells and text extracted from a generated PDF.

## Milestone 6

Lint, the React build and five component tests passed. Both Playwright Chromium cases passed against the live Compose stack. The browser flow registered and authenticated a customer, opened and funded accounts, transferred money, verified both resulting balances, inspected history, downloaded CSV and PDF, and confirmed sign-out. A second case exercised a 360 px sign-in viewport and PostgreSQL readiness. The browser exposed a JDBC result stream left open by the account overview. The fix materializes a bounded result and was verified by twelve sequential overview requests against an eight-connection pool. The Maven run had nine unit cases and twelve integration cases.

## Milestone 7

Maven verification passed nine unit and fifteen PostgreSQL integration cases. Login throttling returned HTTP 429 with Retry-After, authenticated customers could not access global audit records, demo funding defaulted to disabled, and a held account lock produced an HTTP 503 followed by a successful retry with the original key after release. ESLint, the frontend build and five component tests passed.

## Milestone 8

Actionlint 1.7.12 accepted the GitHub Actions workflow. The local npm advisory check found zero frontend vulnerabilities. The browser job's build, smoke and Playwright commands passed against an isolated Compose stack on port 3010 with a fresh PostgreSQL volume. GitHub Actions requires a remote repository to verify hosted execution. This workspace has no GitHub remote.

## Milestone 9 and final verification

| Check | Observed result |
| --- | --- |
| Maven `verify` | 9 unit cases and 17 integration cases passed. The final additions verified generated OpenAPI and PostgreSQL's rejection of a commit containing an incomplete posting. |
| Frontend | ESLint, TypeScript and Vite build passed. Five Vitest component cases passed. |
| Docker Compose | Base and development configurations validated. All three services became healthy. The live smoke check verified readiness, frontend assets and generated OpenAPI through Nginx. |
| Playwright | Both Chromium cases passed. The full case also committed a second transfer, dropped its response, reloaded the browser and found the existing committed receipt. |
| Development server | Vite served the hot-reload client and reached the live OpenAPI endpoint through its API proxy. |
| Database outage | Stopping PostgreSQL produced HTTP 503 and the status-only readiness body `{"status":"DOWN"}`. Restarting it restored readiness and the smoke check passed. |
| Documentation | Local file links resolved across 16 Markdown files. |

The local stack was left running on port 3000. Hosted GitHub Actions execution remains unverified.
