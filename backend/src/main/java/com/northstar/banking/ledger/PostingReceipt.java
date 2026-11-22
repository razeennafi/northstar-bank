/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: PostingReceipt.java
 * Description: Public confirmation of a committed financial posting.
 * Project version: 0.1.0
 */

package com.northstar.banking.ledger;

import java.time.Instant;
import java.util.UUID;

/** Public confirmation of a committed financial posting. */
public record PostingReceipt(UUID id, UUID sourceAccountId, UUID destinationAccountId, String amount,
                             String currency, String kind, String status, Instant completedAt) {
}
