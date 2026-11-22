/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: Account.java
 * Description: Account lifecycle and ownership record.
 * Project version: 0.1.0
 */

package com.northstar.banking.accounts;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Account lifecycle and ownership record. The ledger owns financial balances. */
@Entity
@Table(name = "accounts")
class Account {
    @Id
    private UUID id;
    private UUID customerId;
    @Enumerated(EnumType.STRING)
    private AccountType accountType;
    private String currency;
    private String status;
    private Instant createdAt;

    protected Account() {
    }

    Account(UUID customerId, AccountType type) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.accountType = type;
        this.currency = "CAD";
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    AccountDetails details() {
        return new AccountDetails(id, customerId, accountType, currency, status, createdAt);
    }
}
