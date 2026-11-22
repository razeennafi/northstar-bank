/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: AccountService.java
 * Description: Owns account provisioning, ownership checks and the lock order used by every posting.
 * Project version: 0.1.0
 */

package com.northstar.banking.accounts;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.northstar.banking.audit.AuditService;
import com.northstar.banking.common.BankingException;
import com.northstar.banking.customers.CustomerService;
import com.northstar.banking.ledger.LedgerService;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Owns account provisioning, ownership checks and the lock order used by every posting. */
@Service
public class AccountService {
    private static final int CUSTOMER_ACCOUNT_LIMIT = 20;
    private final AccountRepository accounts;
    private final CustomerService customers;
    private final EntityManager entities;
    private final LedgerService ledger;
    private final AuditService audit;

    public AccountService(AccountRepository accounts, CustomerService customers, EntityManager entities,
                          LedgerService ledger, AuditService audit) {
        this.accounts = accounts;
        this.customers = customers;
        this.entities = entities;
        this.ledger = ledger;
        this.audit = audit;
    }

    /** Opens a customer account and records its creation in the same transaction. */
    @Transactional
    public AccountView create(UUID customer, AccountType type, UUID requestId) {
        if (type == AccountType.SYSTEM) {
            throw new BankingException(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_TYPE", "Choose chequing or savings.");
        }
        // Serializing account creation on the customer row makes the cap race-safe.
        customers.lockActive(customer);
        if (accounts.countByCustomerId(customer) >= CUSTOMER_ACCOUNT_LIMIT) {
            throw new BankingException(HttpStatus.CONFLICT, "ACCOUNT_LIMIT", "The account limit has been reached.");
        }
        Account account = new Account(customer, type);
        entities.persist(account);
        var details = account.details();
        audit.success(customer, "ACCOUNT_OPENED", "ACCOUNT", details.id(), requestId);
        return view(details, BigDecimal.ZERO.setScale(2));
    }

    /** Combines owned account details with balances derived from committed ledger entries. */
    public List<AccountView> list(UUID customer) {
        var owned = accounts.findByCustomerIdOrderByCreatedAtAscIdAsc(customer)
                .stream().map(Account::details).toList();
        var balances = ledger.balances(owned.stream().map(AccountDetails::id).toList());
        return owned.stream().map(account -> view(account, balances.getOrDefault(account.id(), LedgerService.ZERO))).toList();
    }

    /** Returns one account view after verifying that the caller owns it. */
    public AccountView get(UUID customer, UUID accountId) {
        var account = requireOwned(customer, accountId);
        return view(account, ledger.balance(accountId));
    }

    /** Resolves the account within the customer’s ownership scope. */
    public AccountDetails requireOwned(UUID customer, UUID accountId) {
        return accounts.findByIdAndCustomerId(accountId, customer)
                .map(Account::details).orElseThrow(AccountService::unavailable);
    }

    /**
     * Takes both database row locks in global UUID order and retains them to
     * the end of the caller's transaction, even when money moves in reverse.
     *
     * @param source debited account ID
     * @param destination credited account ID
     * @return locked account snapshots in source/destination order
     * @throws BankingException if either account is unavailable
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AccountPair lockForPosting(UUID source, UUID destination) {
        if (source.compareTo(destination) < 0) {
            return new AccountPair(lock(source), lock(destination));
        }
        var to = lock(destination);
        return new AccountPair(lock(source), to);
    }

    public record AccountPair(AccountDetails source, AccountDetails destination) {
    }

    /** Uses a shared response for accounts that are missing or inaccessible. */
    public static BankingException unavailable() {
        return new BankingException(HttpStatus.NOT_FOUND, "ACCOUNT_UNAVAILABLE", "This account is unavailable.");
    }

    /** Obtains the row lock used by posting operations. */
    private AccountDetails lock(UUID id) {
        return accounts.findForPosting(id).map(Account::details).orElseThrow(AccountService::unavailable);
    }

    /** Converts the internal balance into the exact decimal string exposed by the API. */
    private AccountView view(AccountDetails account, BigDecimal balance) {
        return new AccountView(account.id(), account.type(), account.currency(), account.status(),
                account.createdAt(), balance.toPlainString());
    }
}
