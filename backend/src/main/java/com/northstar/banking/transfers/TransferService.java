/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: TransferService.java
 * Description: Fronts the transaction boundary implemented by TransferPosting.
 * Project version: 0.1.0
 */

package com.northstar.banking.transfers;

import java.util.UUID;
import com.northstar.banking.audit.AuditService;
import com.northstar.banking.common.BankingException;
import com.northstar.banking.ledger.PostingReceipt;
import org.springframework.stereotype.Service;

/**
 * Fronts the transaction boundary implemented by TransferPosting. A domain
 * rejection is audited only after the posting transaction has rolled back.
 */
@Service
public class TransferService {
    private final TransferPosting posting;
    private final AuditService audit;

    public TransferService(TransferPosting posting, AuditService audit) {
        this.posting = posting;
        this.audit = audit;
    }

    /** Dispatches a posting and records a business rejection outside the posting transaction. */
    public Outcome transfer(UUID actor, UUID key, TransferRequest request, UUID requestId) {
        try {
            return posting.execute(actor, key, request, requestId);
        } catch (BankingException rejection) {
            audit.rejected(actor, "TRANSFER", "TRANSFER", null, requestId);
            throw rejection;
        }
    }

    /** Retrieves a customer’s original receipt by posting ID. */
    public PostingReceipt find(UUID actor, UUID id) {
        return posting.find(actor, id);
    }

    /** Looks up a committed receipt when a client only knows its retry key. */
    public PostingReceipt findByKey(UUID actor, UUID key) {
        return posting.findByKey(actor, key);
    }

    /** Identifies an idempotent replay without changing the original receipt. */
    public record Outcome(PostingReceipt receipt, boolean replayed) {
    }
}
