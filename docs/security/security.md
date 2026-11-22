# Security and audit operations

## Browser boundary

The browser stores the `NORTHSTAR_SESSION` cookie. It is HttpOnly, SameSite Strict and Secure by default. The loopback HTTP Compose profile sets Secure to false. Use TLS and enable Secure cookies in any HTTPS deployment. Login rotates the session ID and drops the pre-login CSRF token. Logout invalidates the session and clears its cookie.

The CSRF endpoint issues a session-bound token. The API client sends the issued header and value on every mutation. React renders external values as text. Nginx serves the app from the same origin as the API, caps request bodies and sets a content security policy. Spring Security uses an explicit allowlist, and cross-origin credentials are unavailable because the backend grants no CORS origins.

The service uses PBKDF2-HMAC-SHA-256 with 600,000 iterations and a random 16-byte salt. Login returns a generic credential error. A missing user still pays the password verification cost. Rate limiting counts eight login attempts per normalized identity and ninety per transport peer in a one-minute window. The proxy shares the peer limit across its traffic. The limiter is bounded, process-local and resets on restart. This design fits a local single-instance demo. A deployed multi-instance system would need a shared rate limiter and a trusted proxy policy.

## Data access and errors

The current authenticated customer UUID scopes account lists, account details, originating transfers, history and statements. A destination may belong to another Northstar customer, and the API exposes no destination customer profile or balance. Caller-supplied account IDs and transfer keys never establish authorization. SQL values are bound through JPA or JDBC parameters. Error payloads provide a safe code, message, UTC timestamp and request ID.

`BANK_DEMO_FUNDING_ENABLED` defaults to false. A customer must own an active account to receive the fixed CAD 500 grant. The SQL schema restricts grants to one per account and ties them to the system funding account. This mechanism uses only simulated money.

## Audit inspection for development operators

Audit data stays in PostgreSQL. Operators with access to the local Docker project and database role can read a bounded projection. Customer sessions have no route to global audit data.

```powershell
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT action, resource_type, outcome, created_at, request_id FROM audit_events ORDER BY created_at DESC LIMIT 50"'
```

Audit events identify actors and request correlations and avoid storing credentials, session data, submitted email, and error strings. Financial success audit records commit with the postings. For a rejected transfer, the orchestrator records the rejection after rollback in a separate transaction. Authentication failures and denied account access record their own events. The service records statement generation after the report stream finishes producing data. Proxy, socket and database failures can prevent independent audit logging. Transport errors remain separate from the financial ledger.

Sessions live in the application process. Account state transitions and forced session revocation would need operator controls before a deployment beyond local demonstration. Backups, a separate migration role, centralized audit collection, alerting, external identity integration and independent security review belong to a deployment design.
