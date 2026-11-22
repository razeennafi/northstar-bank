# HTTP API

The API prefix is `/api/v1`. Read the generated OpenAPI JSON at `GET /api/v1/openapi`. HTTP operations use JSON except for statement downloads. Monetary responses carry decimal strings in CAD and timestamps use UTC. A customer session and CSRF header are required for protected mutations.

## Authentication

| Request | Input | Result |
| --- | --- | --- |
| `GET /auth/csrf` | Existing session cookie, if any | `200` with `headerName` and `token`. Include the named header on subsequent POST requests. |
| `POST /auth/register` | `fullName`, `email`, `password` | `201` and a profile. Email is normalized and unique. Password length is 12–128 characters. |
| `POST /auth/login` | `email`, `password` | `200` and a profile. The session cookie is rotated and the browser must request a fresh CSRF token. |
| `GET /auth/me` | Session cookie | `200` and `id`, `fullName`, `email`, `createdAt`, or `401` when a session is required. |
| `POST /auth/logout` | Session cookie and current CSRF header | `204` after session invalidation. |

## Accounts, activity and transfers

| Request | Input | Result |
| --- | --- | --- |
| `GET /accounts` | Session cookie | `200` with `accounts`, `totalBalance` and `demoFundingEnabled`. |
| `POST /accounts` | `type` of `CHEQUING` or `SAVINGS` | `201` with the new account and a zero balance. |
| `GET /accounts/{accountId}` | Owned account UUID | `200` with the account and authoritative balance. |
| `GET /accounts/{accountId}/transactions` | Optional `page`, `size`, `sort` of `ASC` or `DESC`, `from` and `to` | `200` with `items`, `page`, `size` and `hasNext`. Pages start at zero, and size is between 1 and 100. Date filters include both UTC calendar days. |
| `POST /demo/funding` | `accountId` for an active owned account | `201` and a fixed grant receipt. A second grant returns `409`. Requires the demo environment flag. |
| `POST /transfers` | UUID `Idempotency-Key` header plus transfer body | `201` and a committed receipt. An equivalent replay returns `200` and the same receipt. |
| `GET /transfers/{transferId}` | Transfer UUID | `200` and the receipt for the original requester. |
| `GET /transfers/by-key/{key}` | Original requester and idempotency UUID | `200` and the committed receipt or `404` if no committed matching request is visible yet. |

### Transfer request body example

```json
{
  "sourceAccountId": "88c95c5c-fbdf-4dd8-965a-cf9935f4de78",
  "destinationAccountId": "f39a0fe8-00ce-4afd-84fa-abd51e6dd315",
  "amount": "125.50",
  "currency": "CAD"
}
```

Substitute IDs from the authorized demo workflow. The API rejects same-account transfers, unsupported currencies, nonpositive values, more than two fractional places and amounts outside `numeric(19,2)`. A receipt includes `id`, source and destination IDs, amount, currency, kind, status and completion instant.

## Statements

Use the owned `accountId` and inclusive UTC `from` and `to` dates in ISO `YYYY-MM-DD` format. A period can span at most 366 days.

| Request | Result |
| --- | --- |
| `GET /statements` | Summary with opening and closing balances, credits, debits, entry count and PDF availability. |
| `GET /statements/download` | Attachment with `format=CSV` or `format=PDF`. CSV streams from the ledger snapshot. PDF is available for up to 1,500 entries. |

## Errors and retry

Error JSON contains `code`, `message`, `timestamp` and `requestId`. Normal response codes include `400` for invalid input, `401` for missing authentication or invalid credentials, `403` for CSRF or access denial, `404` for an unavailable account or lookup, `409` for a business conflict, `429` for login throttling, and `503` for transient database or reporting capacity problems.

If the server sends a receipt, the transfer committed. A response timeout or server error can leave the outcome unknown. Look up the original idempotency key. If a receipt remains unavailable, retry the original transfer body and key together. A mismatch under a previously used key returns `409 IDEMPOTENCY_CONFLICT`.
