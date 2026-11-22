# Transfer sequences

## Successful posting

```mermaid
sequenceDiagram
    actor Customer
    participant UI as React
    participant API as Transfer controller
    participant TX as Transaction service
    participant DB as PostgreSQL
    Customer->>UI: Review and confirm
    UI->>API: POST payload with session, CSRF and key
    API->>TX: Actor, request and idempotency key
    TX->>DB: Begin and claim requester/key
    TX->>DB: Lock account IDs in UUID order
    TX->>DB: Read source balance after locks
    TX->>DB: Insert transfer, debit, credit and success audit
    TX->>DB: Commit and validate deferred constraints
    DB-->>TX: Commit complete
    TX-->>API: Receipt
    API-->>UI: 201 Created
```

## Rejection and rollback

```mermaid
sequenceDiagram
    participant API as Transfer controller
    participant S as Transfer service
    participant T as Transaction service
    participant DB as PostgreSQL
    API->>S: Submit validated HTTP request
    S->>T: Call transactional posting method
    T->>DB: Claim key and lock accounts
    alt Insufficient funds
        T->>DB: Roll back key claim
        T-->>S: Domain rejection
        S->>DB: Commit rejection audit in new transaction
        S-->>API: 409 Conflict
    else Credit insert failure
        T->>DB: Insert debit and attempt credit
        DB-->>T: Database error
        T->>DB: Roll back debit, transfer and claim
        T-->>API: Safe server error
    end
```

A matching replay returns HTTP 200 with the original receipt. An incompatible payload for that key returns HTTP 409. A transfer-key lookup that returns HTTP 404 has found no committed receipt yet. An in-flight request can still commit, so any retry preserves the original key and details.
