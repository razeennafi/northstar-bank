/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: TransferPosting.java
 * Description: Performs a posting inside one PostgreSQL transaction.
 * Project version: 0.1.0
 */

package com.northstar.banking.transfers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import com.northstar.banking.accounts.AccountService;
import com.northstar.banking.accounts.AccountType;
import com.northstar.banking.audit.AuditService;
import com.northstar.banking.common.BankingException;
import com.northstar.banking.ledger.LedgerService;
import com.northstar.banking.ledger.PostingReceipt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performs a posting inside one PostgreSQL transaction. Separate public
 * entry points let Spring's proxy create the boundary before this logic runs.
 */
@Service
public class TransferPosting {
    private final TransferRequestRepository requests;
    private final AccountService accounts;
    private final LedgerService ledger;
    private final AuditService audit;

    TransferPosting(TransferRequestRepository requests, AccountService accounts, LedgerService ledger, AuditService audit) {
        this.requests = requests;
        this.accounts = accounts;
        this.ledger = ledger;
        this.audit = audit;
    }

    /**
     * Executes or replays one customer request. At READ COMMITTED the balance
     * query sees postings that committed while this request waited for a lock.
     * The caller receives a success only once Spring commits the transaction.
     *
     * @param actor authenticated customer ID
     * @param key caller-supplied idempotency UUID
     * @param request transfer payload
     * @param requestId request correlation identifier
     * @return the committed receipt and whether the same request was replayed
     * @throws BankingException when account, funds, amount or key checks fail
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 8)
    public TransferService.Outcome execute(UUID actor, UUID key, TransferRequest request, UUID requestId) {
        BigDecimal amount = request.canonicalAmount();
        UUID candidate = UUID.randomUUID();
        String fingerprint = fingerprint(request, amount);
        requests.boundLockWait();
        if (!requests.claim(actor, key, fingerprint, candidate)) {
            // Our INSERT waited for the competing transaction. A new statement
            // sees the winning committed key under READ COMMITTED.
            var previous = requests.find(actor, key).orElseThrow(TransferPosting::notFound);
            if (!previous.fingerprint().equals(fingerprint)) {
                throw new BankingException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                        "This key was already used for different transfer details.");
            }
            return new TransferService.Outcome(find(actor, previous.transferId()), true);
        }

        UUID source = request.sourceAccountId();
        UUID destination = request.destinationAccountId();
        var locked = accounts.lockForPosting(source, destination);
        if (!actor.equals(locked.source().customerId()) || !locked.source().active()
                || !locked.destination().active() || locked.destination().type() == AccountType.SYSTEM) {
            throw AccountService.unavailable();
        }
        if (ledger.balance(source).compareTo(amount) < 0) {
            throw new BankingException(HttpStatus.CONFLICT, "INSUFFICIENT_FUNDS",
                    "The source account has insufficient funds.");
        }
        var receipt = ledger.post(candidate, actor, source, destination, amount, LedgerService.Kind.TRANSFER);
        audit.success(actor, "TRANSFER", "TRANSFER", receipt.id(), requestId);
        return new TransferService.Outcome(receipt, false);
    }

    /** Scopes a posting lookup to its original requester. */
    public PostingReceipt find(UUID actor, UUID id) {
        return ledger.findRequested(actor, id).orElseThrow(TransferPosting::notFound);
    }

    /** Resolves the requester’s idempotency claim to its stored receipt. */
    public PostingReceipt findByKey(UUID actor, UUID key) {
        return find(actor, requests.find(actor, key).orElseThrow(TransferPosting::notFound).transferId());
    }

    /** Hashes normalized transfer details to detect changed input under an existing key. */
    private String fingerprint(TransferRequest request, BigDecimal amount) {
        var canonical = request.sourceAccountId() + "|" + request.destinationAccountId() + "|"
                + amount.toPlainString() + "|" + request.currency();
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java runtime lacks SHA-256", exception);
        }
    }

    /** Builds the owner-safe response used when no committed receipt is visible. */
    private static BankingException notFound() {
        return new BankingException(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", "No committed transfer was found.");
    }
}
