/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: AuditService.java
 * Description: Persists structured events using bound SQL parameters.
 * Project version: 0.1.0
 */

package com.northstar.banking.audit;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists structured events using bound SQL parameters. Successful financial
 * writes join the caller's transaction. Rejections can survive a rollback.
 */
@Service
public class AuditService {
    private final JdbcClient jdbc;

    public AuditService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Records an audit event within the caller’s transaction. */
    public void success(UUID actor, String action, String resource, UUID resourceId, UUID requestId) {
        insert(actor, action, resource, resourceId, requestId, "SUCCESS");
    }

    /** Uses a separate transaction so the rejection remains visible after a failed posting rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(UUID actor, String action, String resource, UUID resourceId, UUID requestId) {
        insert(actor, action, resource, resourceId, requestId, "REJECTED");
    }

    /** Binds audit fields as SQL parameters and writes the event with its request ID. */
    private void insert(UUID actor, String action, String resource, UUID resourceId, UUID requestId,
                        String outcome) {
        jdbc.sql("""
                INSERT INTO audit_events(id, actor_id, action, resource_type, resource_id, outcome, request_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)
                .param(UUID.randomUUID()).param(actor).param(action).param(resource)
                .param(resourceId).param(outcome).param(requestId).update();
    }
}
