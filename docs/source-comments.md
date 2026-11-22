# Source comments

Source headers credit Razeen Abdun Nafi as the portfolio author and list the header date, filename, responsibility and release version. The date records when the headers were added and the version identifies the project release.

Comments explain behaviour where the reason matters, including transaction boundaries, lock ordering, balance and statement reads, CSRF rotation, request cancellation and recovery after a lost transfer response.

Strict JSON files and generated files use their native formats. Applied Flyway migrations retain their original contents so their recorded checksums remain valid. The schema guide describes those files and their constraints. [Database schema](database/schema.md)

## Files that need companion explanations

| File | What its sections do |
| --- | --- |
| `frontend/package.json` | Scripts run development, builds, lint, component tests, and browser tests. Dependencies ship with the app. Development dependencies supply build and test tooling. |
| `frontend/tsconfig.json` | Compiler options control type checking, JSX and module resolution. The include list brings application code and test configuration into the type check. Vite emits the production bundle. |
| `frontend/package-lock.json` | npm records the resolved dependency graph and integrity hashes so `npm ci` reproduces the installation. |
| `V1__customers_credentials_audit.sql` | Creates profiles, one credential record per customer, and audit history. The email constraint prevents duplicate identities and the audit index supports recent-event lookups. |
| `V2__accounts_and_ledger.sql` | Creates accounts, transfers and entries, seeds the system funding account, and limits the demo grant to one per destination. Indexes support owner and activity queries. Rewrite guards preserve financial records, and deferred triggers verify both entries at commit. |
| `V3__transfer_idempotency.sql` | Stores the requester-scoped key, input fingerprint and posting reference. The rewrite guard preserves the mapping so retries can continue to resolve the original posting. |
