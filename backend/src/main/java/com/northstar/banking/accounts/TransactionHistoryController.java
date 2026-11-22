/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: TransactionHistoryController.java
 * Description: Exposes an owner-scoped window of ledger activity in a stable date order.
 * Project version: 0.1.0
 */

package com.northstar.banking.accounts;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.northstar.banking.authentication.CustomerIdentity;
import com.northstar.banking.ledger.LedgerReporting;
import com.northstar.banking.ledger.LedgerWindow;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Exposes an owner-scoped window of ledger activity in a stable date order. */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/transactions")
public class TransactionHistoryController {
    private final AccountService accounts;
    private final LedgerReporting reporting;

    public TransactionHistoryController(AccountService accounts, LedgerReporting reporting) {
        this.accounts = accounts;
        this.reporting = reporting;
    }

    /** Checks ownership and translates query options into a bounded, ordered ledger page. */
    @GetMapping
    HistoryPage history(@AuthenticationPrincipal CustomerIdentity customer, @PathVariable UUID accountId,
                        @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                        @RequestParam(defaultValue = "DESC") Sort sort,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        accounts.requireOwned(customer.id(), accountId);
        var window = LedgerWindow.history(from, to);
        var items = reporting.page(accountId, window, sort == Sort.DESC, size, page);
        boolean hasNext = items.size() > size;
        return new HistoryPage(hasNext ? items.subList(0, size) : items, page, size, hasNext);
    }

    enum Sort { ASC, DESC }

    record HistoryPage(List<LedgerReporting.Entry> items, int page, int size, boolean hasNext) {
    }
}
