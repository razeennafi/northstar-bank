/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: Credential.java
 * Description: Persists the adaptive password hash without holding a clear-text password.
 * Project version: 0.1.0
 */

package com.northstar.banking.authentication;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persists the adaptive password hash without holding a clear-text password. */
@Entity
@Table(name = "credentials")
class Credential {
    @Id
    private UUID customerId;
    private String passwordHash;
    private Instant passwordUpdatedAt;

    protected Credential() {
    }

    Credential(UUID customerId, String passwordHash) {
        this.customerId = customerId;
        this.passwordHash = passwordHash;
        this.passwordUpdatedAt = Instant.now();
    }

    String hash() {
        return passwordHash;
    }
}
