/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: StatementServiceTest.java
 * Description: Tests statement date boundaries and spreadsheet-safe CSV escaping.
 * Project version: 0.1.0
 */

package com.northstar.banking.statements;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import com.northstar.banking.common.BankingException;
import com.northstar.banking.ledger.LedgerWindow;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementServiceTest {
    /** Exercises spreadsheet formula prefixes and embedded quotes before values are exported as CSV. */
    @Test
    void spreadsheetFormulaPrefixesAreNeutralizedAndQuotesAreEscaped() {
        assertThat(StatementService.csvCell(" \t=2+2")).isEqualTo("\"' \t=2+2\"");
        assertThat(StatementService.csvCell("@SUM(1,2)")).isEqualTo("\"'@SUM(1,2)\"");
        assertThat(StatementService.csvCell("Northstar \"demo\"")).isEqualTo("\"Northstar \"\"demo\"\"\"");
    }

    /** Checks that inclusive UTC dates become a half-open database range and that invalid ranges are rejected. */
    @Test
    void statementDatesAreInclusiveCalendarDaysInUtc() {
        var from = LocalDate.parse("2026-02-01");
        var to = LocalDate.parse("2026-02-28");
        var window = LedgerWindow.statement(from, to);
        assertThat(window.start()).isEqualTo(OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        assertThat(window.endExclusive()).isEqualTo(OffsetDateTime.parse("2026-03-01T00:00:00Z"));
        assertThatThrownBy(() -> LedgerWindow.statement(to, from)).isInstanceOf(BankingException.class);
        assertThatThrownBy(() -> LedgerWindow.statement(from, from.plusDays(366))).isInstanceOf(BankingException.class);
    }
}
