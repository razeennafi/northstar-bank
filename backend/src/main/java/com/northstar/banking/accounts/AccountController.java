/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: AccountController.java
 * Description: Exposes only the authenticated customer's account views.
 * Project version: 0.1.0
 */

package com.northstar.banking.accounts;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.northstar.banking.authentication.CustomerIdentity;
import com.northstar.banking.common.RequestIdFilter;
import com.northstar.banking.ledger.LedgerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Exposes only the authenticated customer's account views. */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accounts;
    private final boolean demoFundingEnabled;

    public AccountController(AccountService accounts, @Value("${bank.demo-funding-enabled:false}") boolean demoFundingEnabled) {
        this.accounts = accounts;
        this.demoFundingEnabled = demoFundingEnabled;
    }

    /** Builds the dashboard response from the customer’s accounts and a decimal total. */
    @GetMapping
    AccountOverview list(@AuthenticationPrincipal CustomerIdentity customer) {
        var views = accounts.list(customer.id());
        var total = views.stream().map(AccountView::balance).map(BigDecimal::new)
                .reduce(LedgerService.ZERO, BigDecimal::add);
        return new AccountOverview(views, total.toPlainString(), demoFundingEnabled);
    }

    /** Loads the requested account using the authenticated customer as the owner. */
    @GetMapping("/{accountId}")
    AccountView view(@AuthenticationPrincipal CustomerIdentity customer, @PathVariable UUID accountId) {
        return accounts.get(customer.id(), accountId);
    }

    /** Validates the selected account type and passes the request ID to the audited creation workflow. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountView create(@AuthenticationPrincipal CustomerIdentity customer,
                       @Valid @RequestBody CreateAccount body, HttpServletRequest request) {
        return accounts.create(customer.id(), body.type(), RequestIdFilter.id(request));
    }

    record CreateAccount(@NotNull AccountType type) {
    }

    record AccountOverview(List<AccountView> accounts, String totalBalance, boolean demoFundingEnabled) {
    }
}
