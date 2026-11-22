# Integration and API testing

Run `mvn -f backend/pom.xml verify` with a working local Docker engine. Failsafe starts the `*IT` classes. Testcontainers allocates temporary PostgreSQL 17.12 containers and random bound ports. Spring Boot applies Flyway and validates the JPA schema. Each test creates its own synthetic identities and accounts.

MockMvc exercises the real servlet filter chain. Integration tests issue independent requests and inspect rows after the response, with no test-managed rollback. The credit failure test installs a test-only constraint trigger that fails the credit after the debit insert. It removes the trigger in a finally block and verifies a retry with the original key. A repeated overview case verifies that JDBC connections return to the pool.

Check `backend/target/failsafe-reports` when an integration test fails. An early startup failure may indicate unavailable Docker, a failed image pull, a migration error or a port binding problem. A post-response assertion points to a contract or state change. Testcontainers exercises PostgreSQL behavior and does not emulate an external payment network or long-running production load.
