/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: LedgerReporting.java
 * Description: Reads committed ledger activity with stable ordering, aggregate totals and a bounded cursor.
 * Project version: 0.1.0
 */

package com.northstar.banking.ledger;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads committed ledger activity with stable ordering, aggregate totals and a bounded cursor. */
@Repository
public class LedgerReporting {
    private static final String ENTRIES = """
            SELECT e.id, e.transfer_id, e.direction, e.amount, e.created_at, t.kind, t.status
            FROM ledger_entries e JOIN transfers t ON t.id = e.transfer_id
            WHERE e.account_id = ?
            """;
    private final JdbcClient jdbc;
    private final JdbcTemplate template;

    public LedgerReporting(JdbcClient jdbc, JdbcTemplate template) {
        this.jdbc = jdbc;
        this.template = template;
    }

    /** Fetches a bounded page with stable ordering and the extra row used to detect another page. */
    public List<Entry> page(UUID account, LedgerWindow window, boolean descending, int size, int page) {
        var parameters = new ArrayList<Object>();
        parameters.add(account);
        var sql = new StringBuilder(ENTRIES);
        if (window.start() != null) {
            sql.append(" AND e.created_at >= ?");
            parameters.add(window.start());
        }
        if (window.endExclusive() != null) {
            sql.append(" AND e.created_at < ?");
            parameters.add(window.endExclusive());
        }
        sql.append(descending ? " ORDER BY e.created_at DESC, e.id DESC" : " ORDER BY e.created_at, e.id");
        sql.append(" LIMIT ? OFFSET ?");
        parameters.add(size + 1);
        parameters.add(page * size);
        return jdbc.sql(sql.toString()).params(parameters).query((row, number) -> entry(row)).list();
    }

    /** Aggregates prior activity into the opening balance and period activity into credits and debits. */
    public Totals totals(UUID account, LedgerWindow window) {
        return jdbc.sql("""
                SELECT coalesce(sum(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END)
                    FILTER (WHERE created_at < :start), 0.00) AS opening,
                    coalesce(sum(amount) FILTER (WHERE direction = 'CREDIT' AND created_at >= :start), 0.00) AS credits,
                    coalesce(sum(amount) FILTER (WHERE direction = 'DEBIT' AND created_at >= :start), 0.00) AS debits,
                    count(*) FILTER (WHERE created_at >= :start) AS entries
                FROM ledger_entries WHERE account_id = :account AND created_at < :end
                """).param("start", window.start()).param("end", window.endExclusive()).param("account", account)
                .query((row, number) -> new Totals(row.getBigDecimal("opening"), row.getBigDecimal("credits"),
                        row.getBigDecimal("debits"), row.getLong("entries"))).single();
    }

    /**
     * Streams entries through a PostgreSQL cursor. The caller supplies a
     * transaction, because the driver otherwise buffers the whole result.
     */
    public void consume(UUID account, LedgerWindow window, Consumer<Entry> onEntry) {
        template.query(connection -> {
            var statement = connection.prepareStatement(ENTRIES
                    + " AND e.created_at >= ? AND e.created_at < ? ORDER BY e.created_at, e.id");
            statement.setObject(1, account);
            statement.setObject(2, window.start());
            statement.setObject(3, window.endExclusive());
            statement.setFetchSize(256);
            return statement;
        }, (RowCallbackHandler) row -> onEntry.accept(entry(row)));
    }

    public record Entry(UUID id, UUID transferId, String direction, String amount, Instant createdAt,
                        String type, String status) {
    }

    public record Totals(BigDecimal opening, BigDecimal credits, BigDecimal debits, long entries) {
        public BigDecimal closing() {
            return opening.add(credits).subtract(debits);
        }
    }

    /** Maps a ledger row to a report entry with its posting reference and timestamp. */
    private Entry entry(ResultSet row) throws SQLException {
        return new Entry(row.getObject("id", UUID.class), row.getObject("transfer_id", UUID.class),
                row.getString("direction"), row.getBigDecimal("amount").toPlainString(),
                row.getObject("created_at", OffsetDateTime.class).toInstant(), row.getString("kind"),
                row.getString("status"));
    }
}
