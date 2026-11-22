/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: DemoFundingService.java
 * Description: Posts a one-time demo grant from the system account when explicitly enabled.
 * Project version: 0.1.0
 */

package com.northstar.banking.accounts;

import java.math.BigDecimal;
import java.util.UUID;
import com.northstar.banking.audit.AuditService;
import com.northstar.banking.common.BankingException;
import com.northstar.banking.ledger.LedgerService;
import com.northstar.banking.ledger.PostingReceipt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Posts a one-time demo grant from the system account when explicitly enabled. */
@Service
public class DemoFundingService {
    private static final UUID SYSTEM_ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final BigDecimal DEMO_AMOUNT = new BigDecimal("500.00");
    private final AccountService accounts;
    private final LedgerService ledger;
    private final AuditService audit;
    private final boolean enabled;

    public DemoFundingService(AccountService accounts, LedgerService ledger, AuditService audit,
                              @Value("${bank.demo-funding-enabled:false}") boolean enabled) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.audit = audit;
        this.enabled = enabled;
    }

    /**
     * Applies exactly one fixed grant to an active owned account. Checks and
     * posting run under the same locks so concurrent grant calls cannot overlap.
     */
    @Transactional
    public PostingReceipt fund(UUID actor, UUID accountId, UUID requestId) {
        if (!enabled) {
            throw new BankingException(HttpStatus.NOT_FOUND, "DEMO_UNAVAILABLE", "Demo funding is unavailable.");
        }
        accounts.requireOwned(actor, accountId);
        var pair = accounts.lockForPosting(SYSTEM_ACCOUNT, accountId);
        if (!pair.destination().active() || !pair.source().active()) {
            throw AccountService.unavailable();
        }
        if (ledger.hasDemoFunding(accountId)) {
            throw new BankingException(HttpStatus.CONFLICT, "DEMO_ALREADY_FUNDED", "This account already received demo funds.");
        }
        var posting = ledger.post(UUID.randomUUID(), actor, SYSTEM_ACCOUNT, accountId, DEMO_AMOUNT,
                LedgerService.Kind.DEMO_FUNDING);
        audit.success(actor, "DEMO_FUNDED", "TRANSFER", posting.id(), requestId);
        return posting;
    }
}
