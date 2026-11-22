/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: LedgerWindow.java
 * Description: Converts inclusive calendar dates into half-open UTC bounds for indexed SQL queries.
 * Project version: 0.1.0
 */

package com.northstar.banking.ledger;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import com.northstar.banking.common.BankingException;
import org.springframework.http.HttpStatus;

/** Converts inclusive calendar dates into half-open UTC bounds for indexed SQL queries. */
public record LedgerWindow(OffsetDateTime start, OffsetDateTime endExclusive) {
    /** Converts optional history dates into database query bounds. */
    public static LedgerWindow history(LocalDate from, LocalDate to) {
        if (!valid(from) || !valid(to) || from != null && to != null && from.isAfter(to)) {
            throw invalid("Choose a valid start and end date.");
        }
        return new LedgerWindow(from == null ? null : utcStart(from), to == null ? null : utcStart(to.plusDays(1)));
    }

    /** Validates the required statement interval before building its UTC bounds. */
    public static LedgerWindow statement(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw invalid("Enter both statement dates.");
        }
        LedgerWindow window = history(from, to);
        if (ChronoUnit.DAYS.between(from, to) >= 366) {
            throw invalid("Choose a statement period of 366 days or fewer.");
        }
        return window;
    }

    private static boolean valid(LocalDate date) {
        return date == null || date.getYear() >= 1970 && date.getYear() <= 9998;
    }

    private static OffsetDateTime utcStart(LocalDate date) {
        return date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private static BankingException invalid(String message) {
        return new BankingException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", message);
    }
}
