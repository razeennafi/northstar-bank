# System architecture

```mermaid
flowchart LR
    A[Browser] -->|HTTP on loopback| N[Nginx or Vite]
    N -->|Same-origin JSON and downloads| C[Spring MVC controllers]
    C --> S[Domain services]
    B[Spring Security session and CSRF filters] -. authorizes .-> C
    S --> H[Spring Data JPA and Hibernate]
    S --> J[JDBC repositories]
    H --> P[(PostgreSQL)]
    J --> P
    F[Flyway migrations] --> P
    P --> R[Actuator readiness probe]
```

The reverse proxy forwards HTTP to the backend over the Compose network. Spring Security checks the session and mutation token before a controller runs. DTOs define the public request and response shapes. Profiles and accounts use JPA to map rows to durable domain objects. Posting and reporting use bound SQL for explicit commit checks, aggregates and cursor controls.

## Module dependencies

```mermaid
flowchart TD
    AUTH[Authentication] --> CUST[Customers]
    AUTH --> AUD[Audit]
    ACC[Accounts] --> CUST
    ACC --> LED[Ledger and reporting]
    ACC --> AUD
    TRN[Transfers] --> ACC
    TRN --> LED
    TRN --> AUD
    STMT[Statements] --> ACC
    STMT --> LED
    STMT --> AUD
```

The funding service sits in Accounts and uses the same ordered locking and ledger post primitive as Transfers. A ledger query cannot start a transfer or create an account. The Audit module never calls back into a financial service.
