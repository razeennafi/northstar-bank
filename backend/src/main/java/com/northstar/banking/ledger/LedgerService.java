/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: LedgerService.java
 * Description: Owns posting writes and authoritative ledger balances.
 * Project version: 0.1.0
 */

package com.northstar.banking.ledger;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns posting writes and authoritative ledger balances. JDBC binds all
 * values and joins the Spring-managed JPA transaction through the DataSource.
 * Keeping all writes here makes the double-entry invariant easy to review.
 */
@Service
public class LedgerService {
    public static final BigDecimal ZERO = new BigDecimal("0.00");
    public static final String CURRENCY = "CAD";
    private static final String NET_AMOUNT = "CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END";
    private final JdbcClient jdbc;

    public LedgerService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Derives one account’s balance by summing signed ledger entries. */
    public BigDecimal balance(UUID accountId) {
        return jdbc.sql("SELECT COALESCE(SUM(" + NET_AMOUNT + "), 0.00) FROM ledger_entries WHERE account_id = ?")
                .param(accountId).query(BigDecimal.class).single();
    }

    /** Groups balances for the dashboard in one bounded query. */
    public Map<UUID, BigDecimal> balances(List<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("SELECT account_id, SUM(" + NET_AMOUNT + ") AS balance FROM ledger_entries "
                        + "WHERE account_id IN (:ids) GROUP BY account_id")
                .param("ids", accountIds)
                .query((row, number) -> Map.entry(row.getObject("account_id", UUID.class), row.getBigDecimal("balance")))
                // At most one row per owned account. Materializing this bounded
                // result closes JDBC resources before we transform the values.
                .list().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Checks for an existing demo grant before attempting another posting. */
    public boolean hasDemoFunding(UUID accountId) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM transfers WHERE destination_account_id = ? AND kind = 'DEMO_FUNDING')")
                .param(accountId).query(Boolean.class).single();
    }

    /**
     * Records a posting and both ledger rows. The caller holds both account
     * locks and has checked eligibility and spending. A database failure throws
     * an unchecked exception so the enclosing transaction rolls back in full.
     *
     * @param id pre-allocated posting ID
     * @param actor authenticated requester
     * @param source debited account ID
     * @param destination credited account ID
     * @param amount positive exact CAD amount with two decimals
     * @param kind internal operation type used for reporting
     * @return receipt available to the browser after the enclosing transaction commits
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PostingReceipt post(UUID id, UUID actor, UUID source, UUID destination, BigDecimal amount, Kind kind) {
        var created = jdbc.sql("""
                INSERT INTO transfers(id, requester_id, source_account_id, destination_account_id,
                                      amount, currency, kind, status, created_at, completed_at)
                VALUES (?, ?, ?, ?, ?, 'CAD', ?, 'COMPLETED', statement_timestamp(), statement_timestamp())
                RETURNING completed_at
                """).param(id).param(actor).param(source).param(destination).param(amount).param(kind.name())
                .query(OffsetDateTime.class).single();

        // Distinct INSERTs exercise transaction rollback if the credit fails
        // after the debit has already reached PostgreSQL.
        insertEntry(id, source, amount, "DEBIT", created);
        insertEntry(id, destination, amount, "CREDIT", created);
        return new PostingReceipt(id, source, destination, amount.toPlainString(), CURRENCY,
                kind.name(), "COMPLETED", created.toInstant());
    }

    /** Scopes receipt lookup to the customer who submitted the original posting. */
    public Optional<PostingReceipt> findRequested(UUID actor, UUID postingId) {
        return jdbc.sql("SELECT * FROM transfers WHERE requester_id = ? AND id = ? AND kind = 'TRANSFER'")
                .param(actor).param(postingId).query((row, number) -> receipt(row)).optional();
    }

    public enum Kind {
        DEMO_FUNDING, TRANSFER
    }

    /** Writes one side of the posting with the shared transfer ID. */
    private void insertEntry(UUID posting, UUID account, BigDecimal amount, String direction, OffsetDateTime created) {
        jdbc.sql("""
                INSERT INTO ledger_entries(id, transfer_id, account_id, direction, amount, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """).param(UUID.randomUUID()).param(posting).param(account)
                .param(direction).param(amount).param(created).update();
    }

    /** Maps the persisted posting to the public receipt without converting money to floating point. */
    private PostingReceipt receipt(ResultSet row) throws SQLException {
        return new PostingReceipt(row.getObject("id", UUID.class), row.getObject("source_account_id", UUID.class),
                row.getObject("destination_account_id", UUID.class), row.getBigDecimal("amount").toPlainString(),
                row.getString("currency"), row.getString("kind"), row.getString("status"),
                row.getObject("completed_at", OffsetDateTime.class).toInstant());
    }
}
