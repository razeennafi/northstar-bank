/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: TransferRequestRepository.java
 * Description: Owns the durable idempotency mapping.
 * Project version: 0.1.0
 */

package com.northstar.banking.transfers;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Owns the durable idempotency mapping. PostgreSQL arbitrates concurrent
 * requests through the composite primary key, across all application threads.
 */
@Repository
class TransferRequestRepository {
    private final JdbcClient jdbc;

    TransferRequestRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void boundLockWait() {
        // SET LOCAL expires at commit or rollback, so a pooled connection keeps its original setting.
        jdbc.sql("SET LOCAL lock_timeout = '3s'").update();
    }

    /** Attempts an atomic insert so only one transaction can claim a requester’s key. */
    boolean claim(UUID actor, UUID key, String fingerprint, UUID postingId) {
        return jdbc.sql("""
                INSERT INTO transfer_requests(requester_id, request_key, fingerprint, transfer_id)
                VALUES (?, ?, ?, ?) ON CONFLICT (requester_id, request_key) DO NOTHING
                """).param(actor).param(key).param(fingerprint).param(postingId).update() == 1;
    }

    /** Loads the fingerprint and receipt reference for a previously claimed key. */
    Optional<Claim> find(UUID actor, UUID key) {
        return jdbc.sql("SELECT fingerprint, transfer_id FROM transfer_requests WHERE requester_id = ? AND request_key = ?")
                .param(actor).param(key)
                .query((row, number) -> new Claim(row.getString("fingerprint"), row.getObject("transfer_id", UUID.class)))
                .optional();
    }

    record Claim(String fingerprint, UUID transferId) {
    }
}
