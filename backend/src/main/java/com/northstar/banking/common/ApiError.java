/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: ApiError.java
 * Description: Common error payload for controller and security filter responses.
 * Project version: 0.1.0
 */

package com.northstar.banking.common;

import java.time.Instant;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;

/** Common error payload for controller and security filter responses. */
public record ApiError(String code, String message, Instant timestamp, UUID requestId) {
    public static ApiError of(String code, String message, HttpServletRequest request) {
        return new ApiError(code, message, Instant.now(), RequestIdFilter.id(request));
    }
}
