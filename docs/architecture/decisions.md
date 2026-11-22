# Architecture decisions

| Decision | Rationale and trade-off |
| --- | --- |
| Modular monolith | One Java process supports a single ACID transaction across posting components. Microservices could scale components independently and would add network failure modes and cross-service consistency work. |
| PostgreSQL | Numeric amounts, restrictive foreign keys, deferred constraints, row locks and reporting SQL satisfy the core invariants in one place. An embedded test database would make the test harness lighter and give weaker evidence about PostgreSQL behaviour. |
| Exact decimal arithmetic | Java `BigDecimal` and SQL `numeric(19,2)` keep cents exact. Integer minor units could also represent CAD exactly and would require mapping scale and formatting when currencies grow. |
| Double-entry ledger | Every simulated transaction has a verifiable debit and credit. Calculating balances from entries increases query work as the ledger grows. A cached balance would require atomic updates and reconciliation. |
| Ordered pessimistic account locks | PostgreSQL serializes withdrawals across app processes, while a global ordering reduces account deadlocks. Optimistic version checks would suit workloads with less contention and would need safe retry handling. |
| Server-managed sessions | Session rotation, logout and CSRF protection fit a same-origin browser app. Stateless tokens would simplify some replica coordination and would add token storage and revocation decisions. |
| Testcontainers | Integration tests launch the PostgreSQL version used in Compose and validate actual commit and blocking behavior. They require Docker and are slower than isolated unit tests. |
| Flyway migrations | Versioned SQL records constraints, triggers and data evolution. Hibernate validates the expected mapping at startup. Automatic ORM schema updates would simplify prototypes and provide less control over financial constraints and upgrades. |
| JDBC alongside JPA | Profile and account entities use Hibernate mappings. Posting and reporting use JDBC to expose SQL execution order, exact aggregates and cursor behaviour. Both use Spring's shared transaction manager. |
