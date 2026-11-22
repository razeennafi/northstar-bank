/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: AccountView.java
 * Description: Browser-facing account view.
 * Project version: 0.1.0
 */

package com.northstar.banking.accounts;

import java.time.Instant;
import java.util.UUID;

/** Browser-facing account view. Monetary strings preserve the exact decimal value. */
public record AccountView(UUID id, AccountType type, String currency, String status,
                          Instant createdAt, String balance) {
}
