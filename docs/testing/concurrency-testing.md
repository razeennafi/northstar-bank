# Concurrency testing

Run `mvn -f backend/pom.xml verify` with Docker. Two worker threads synchronize on a start latch so both requests compete for PostgreSQL row locks. Futures have bounded waits, and each transfer opens its own Spring transaction.

| Scenario | Expected result |
| --- | --- |
| CAD 400 and CAD 300 withdrawals from CAD 500 | One commits, the other returns insufficient funds. The sum across the two recipients and source remains CAD 500. |
| Same requester and key submitted twice | One posting and one debit/credit pair. The second request replays the receipt. |
| Opposite-direction postings through the same account pair | Both complete while acquiring the account locks in identical UUID order. |
| Independent transaction holds a source lock | Posting returns HTTP 503, the claim rolls back, and the original key succeeds after the lock is released. |

READ COMMITTED takes a fresh snapshot for the balance query after the locks have been acquired. Holding the locks to commit prevents another transaction from spending from an earlier balance on either account. The transfer sets a three-second lock timeout. The API leaves retries to the caller with the same, durable idempotency key. This evidence covers the controlled interleavings above. It does not establish throughput or fairness under sustained load.
