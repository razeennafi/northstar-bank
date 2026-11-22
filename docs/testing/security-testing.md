# Security testing

The PostgreSQL and MockMvc suite verifies canonical emails, adaptive hashes, invalid credentials, session ID rotation, CSRF refresh after login, login throttling, invalid body handling and logout. Account and transfer cases cover missing authentication, foreign account access, requester-scoped transfer lookups, owner-only statement downloads, the default demo-funding gate and denial of customer audit requests.

Playwright complements those checks with a real browser session, a protected account request after sign-out, and same-origin file downloads. Run `npm --prefix frontend audit --audit-level=high` and consult the repository dependency update configuration. TLS deployment, centralized rate limiting, credential recovery, backup integrity and multi-instance session revocation require separate deployment or security work.
