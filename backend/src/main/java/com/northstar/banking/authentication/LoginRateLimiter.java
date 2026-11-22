/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: LoginRateLimiter.java
 * Description: Applies per-identity and per-connection limits before the password KDF.
 * Project version: 0.1.0
 */

package com.northstar.banking.authentication;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.northstar.banking.common.BankingException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Applies per-identity and per-connection limits before the password KDF.
 * Atomic cache updates work across request threads within this local process.
 */
@Service
class LoginRateLimiter {
    static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int IDENTITY_LIMIT = 8;
    private static final int ADDRESS_LIMIT = 90;
    private final Cache<String, Window> attempts = Caffeine.newBuilder().maximumSize(10_000)
            .expireAfterAccess(WINDOW.multipliedBy(2)).build();

    /** Checks request activity against the relevant rate-limit window. */
    void check(String email, String peerAddress) {
        Instant now = Instant.now();
        // Use the transport peer. The proxy path shares an address bucket and
        // callers cannot reset it by supplying a forwarded address header.
        check("peer:" + peerAddress, ADDRESS_LIMIT, now);
        check("identity:" + email.strip().toLowerCase(Locale.ROOT), IDENTITY_LIMIT, now);
    }

    /** Checks request activity against the relevant rate-limit window. */
    private void check(String key, int limit, Instant now) {
        // Compute atomically per key so simultaneous requests cannot miss each other's attempts.
        Window window = attempts.asMap().compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.ends())) {
                return new Window(now.plus(WINDOW), 1);
            }
            return new Window(current.ends(), Math.min(current.count() + 1, limit + 1));
        });
        if (window.count() > limit) {
            throw new BankingException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_THROTTLED",
                    "Too many sign-in attempts. Wait a minute and try again.");
        }
    }

    private record Window(Instant ends, int count) {
    }
}
