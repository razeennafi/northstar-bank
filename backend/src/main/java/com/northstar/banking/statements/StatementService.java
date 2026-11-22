/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: StatementService.java
 * Description: Generates an owned-account statement from a repeatable-read snapshot.
 * Project version: 0.1.0
 */

package com.northstar.banking.statements;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import com.northstar.banking.accounts.AccountService;
import com.northstar.banking.common.BankingException;
import com.northstar.banking.ledger.LedgerReporting;
import com.northstar.banking.ledger.LedgerWindow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates an owned-account statement from a repeatable-read snapshot. The
 * summary and entries share the snapshot even while new transfers commit.
 */
@Service
public class StatementService {
    private static final int PDF_ENTRY_LIMIT = 1500;
    private final AccountService accounts;
    private final LedgerReporting ledger;

    public StatementService(AccountService accounts, LedgerReporting ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    /** Checks account ownership and calculates totals for the selected interval. */
    public StatementSummary preview(UUID actor, UUID account, LedgerWindow window) {
        accounts.requireOwned(actor, account);
        return summary(account, ledger.totals(account, window));
    }

    /** Rejects oversized PDFs while leaving the CSV export available. */
    public void checkPdfSize(StatementSummary summary) {
        if (!summary.pdfAvailable()) {
            throw new BankingException(HttpStatus.BAD_REQUEST, "PDF_ENTRY_LIMIT",
                    "Select a shorter date range or download the CSV.");
        }
    }

    /**
     * Writes a statement directly to the servlet stream on its worker thread.
     * Only PDF page metadata is retained. CSV rows go out as the cursor yields them.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 35)
    public void write(UUID actor, UUID account, LedgerWindow window, StatementFormat format, OutputStream out)
            throws IOException {
        var summary = preview(actor, account, window);
        if (format == StatementFormat.PDF) {
            checkPdfSize(summary);
            try (var report = new PdfStatement(summary, window)) {
                report.start();
                ledger.consume(account, window, report::addEntry);
                report.save(out);
            }
        } else {
            var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            row(writer, "Northstar Bank", "Account", account.toString(), "CAD");
            row(writer, "Period (UTC)", window.start().toLocalDate().toString(),
                    window.endExclusive().toLocalDate().minusDays(1).toString());
            row(writer, "Opening balance", summary.openingBalance(), "Closing balance", summary.closingBalance());
            row(writer, "Total credits", summary.credits(), "Total debits", summary.debits());
            row(writer, "Date (UTC)", "Direction", "Amount (CAD)", "Type", "Status", "Transfer reference");
            ledger.consume(account, window, entry -> row(writer, entry.createdAt().toString(), entry.direction(),
                    entry.amount(), entry.type(), entry.status(), entry.transferId().toString()));
            writer.flush();
        }
    }

    /** Converts exact ledger totals to response strings and indicates PDF availability. */
    private StatementSummary summary(UUID account, LedgerReporting.Totals totals) {
        return new StatementSummary(account, totals.opening().toPlainString(), totals.closing().toPlainString(),
                totals.credits().toPlainString(), totals.debits().toPlainString(), totals.entries(), totals.entries() <= PDF_ENTRY_LIMIT);
    }

    /** Escapes each field before writing one CSV record. */
    private void row(Writer writer, String... cells) {
        try {
            writer.write(Arrays.stream(cells).map(StatementService::csvCell).collect(Collectors.joining(",")));
            writer.write("\r\n");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Quotes embedded delimiters and neutralizes spreadsheet formula prefixes. */
    static String csvCell(String value) {
        String trimmed = value.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0
                || !value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public record StatementSummary(UUID accountId, String openingBalance, String closingBalance,
                                   String credits, String debits, long entries, boolean pdfAvailable) {
    }

    public enum StatementFormat { CSV, PDF }
}
