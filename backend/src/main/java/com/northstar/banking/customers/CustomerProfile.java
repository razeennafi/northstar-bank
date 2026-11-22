/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: CustomerProfile.java
 * Description: Immutable customer information returned by the API.
 * Project version: 0.1.0
 */

package com.northstar.banking.customers;

import java.time.Instant;
import java.util.UUID;

/** Immutable customer information returned by the API. */
public record CustomerProfile(UUID id, String fullName, String email, Instant createdAt) {
}
