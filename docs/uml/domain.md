# Domain class diagram

```mermaid
classDiagram
    class Customer {
        UUID id
        String email
        String status
        profile() CustomerProfile
    }
    class Credential {
        UUID customerId
        String passwordHash
        Instant passwordUpdatedAt
    }
    class Account {
        UUID id
        UUID customerId
        AccountType accountType
        String status
        details() AccountDetails
    }
    class AccountService {
        create(customer, type, requestId) AccountView
        lockForPosting(source, destination) AccountPair
        requireOwned(customer, accountId) AccountDetails
    }
    class TransferRequest {
        UUID sourceAccountId
        UUID destinationAccountId
        BigDecimal amount
        canonicalAmount() BigDecimal
    }
    class TransferService {
        transfer(actor, key, request, requestId) Outcome
    }
    class TransferPosting {
        execute(actor, key, request, requestId) Outcome
    }
    class LedgerService {
        post(id, actor, source, destination, amount, kind) PostingReceipt
        balance(accountId) BigDecimal
    }
    class StatementService {
        preview(actor, account, window) StatementSummary
        write(actor, account, window, format, out)
    }
    class LedgerReporting {
        page(account, window, descending, size, page) List
        totals(account, window) Totals
    }
    Customer "1" -- "1" Credential
    Customer "0..1" -- "0..*" Account
    AccountService --> Account
    AccountService --> LedgerService
    TransferService --> TransferPosting
    TransferPosting ..> TransferRequest
    TransferPosting --> AccountService
    TransferPosting --> LedgerService
    StatementService --> AccountService
    StatementService --> LedgerReporting
```

The relationships between Customer, Credential and Account are database relationships represented by IDs. Public API records contain selected immutable values. With that design, JSON serialization has a bounded shape and does not recursively walk JPA entity graphs.
