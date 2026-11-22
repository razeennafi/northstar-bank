# Learn the Northstar codebase

## Follow a request

1. Open the Transfer Money view in React. It prepares a JSON body and generates a UUID only after the customer reviews it.
2. The API client obtains a CSRF token and sends the payload to Spring Boot through Nginx or Vite. Spring Security identifies the session.
3. `TransferController` maps application records to HTTP. Bean Validation catches malformed input. The account and posting services apply financial and authorization rules.
4. Spring opens a transaction around `TransferPosting.execute`. PostgreSQL claims the request key, locks both account rows, checks the source balance, and stores the transfer, debit, credit and audit.
5. The controller returns the committed receipt. React renders it and reloads the current account balances.

Compare the request in browser developer tools with the [sequence diagrams](../uml/transfers.md) and the [database schema](../database/schema.md).

## Java and Spring for a C++ developer

Java garbage collection reclaims ordinary objects when they become unreachable. Database connections, PDF streams and file handles still need deterministic closure. Java try-with-resources fills a role familiar from C++ RAII for these resources. Java object variables hold references passed by value. Records make useful immutable DTOs when their components are immutable.

Java distinguishes checked exceptions, which usually need a declaration or catch, from unchecked exceptions. Spring's default transaction rollback covers unchecked exceptions and serious errors. Add an explicit rollback rule if a checked exception must roll back writes. The posting service keeps database work inside one transaction and lets write failures propagate. A call within the same object does not cross Spring's transactional proxy. The project uses a separate coordinator and transactional service for that reason.

`@SpringBootApplication` starts an embedded servlet server, scans components and applies dependency-based configuration. Constructor injection gives components their required collaborators. A `@Service` holds business rules. A `@RestController` makes a service reachable over HTTP through request and response DTOs. Spring Data generates repository implementations from interfaces, and their generic parameters describe the entity and key. `@Entity` and `@Id` identify persistent objects for Hibernate. Hibernate handles mapping and validates the Flyway schema at startup.

## Concurrency and resource use

Spring services are shared among request threads. Keep a request's account IDs, amount and intermediate values local to its method. PostgreSQL locks serialize database writes across Java processes. The [concurrency test guide](../testing/concurrency-testing.md) explains how account order and READ COMMITTED protect against overspending and deadlocks.

Hikari pools connections so every request need not start a new database session. Connection and statement lifetime matter even with garbage collection. The account overview regression caught an unclosed JDBC stream. Queries with a bounded result can materialize that small list. Large CSV exports use an active read-only transaction, a fetch-size-limited cursor and a bounded worker queue. Cyclic objects, unbounded queues and oversized query results risk stack or heap exhaustion. Treat Java's `StackOverflowError` and `OutOfMemoryError` as process failures that require diagnosis.

## Tools and evidence

Docker images package a built app. Containers run those images on a project network. The PostgreSQL named volume holds database state across restarts. Unit tests exercise isolated calculation and validation rules. Testcontainers launches temporary PostgreSQL instances to test actual schema and transaction behaviour. MockMvc drives the Spring security and HTTP layers. Playwright operates Chromium against the running stack and verifies actual statement downloads. CI runs the same commands on a fresh runner. Read the [testing strategy](../testing/testing-strategy.md) before interpreting a green test run.
