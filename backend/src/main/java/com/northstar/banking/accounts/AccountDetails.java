/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: AccountDetails.java
 * Description: Transaction-internal account attributes used for ownership and eligibility checks.
 * Project version: 0.1.0
 */

package com.northstar.banking.accounts;

import java.time.Instant;
import java.util.UUID;

/** Transaction-internal account attributes used for ownership and eligibility checks. */
public record AccountDetails(UUID id, UUID customerId, AccountType type, String currency,
                             String status, Instant createdAt) {
    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
