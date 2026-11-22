/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: RequestIdFilter.java
 * Description: Creates a server-owned request ID before the security chain so audit events, HTTP errors and diagnostic logs can refer to the same request.
 * Project version: 0.1.0
 */

package com.northstar.banking.common;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Creates a server-owned request ID before the security chain so audit events,
 * HTTP errors and diagnostic logs can refer to the same request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final String ATTRIBUTE = RequestIdFilter.class.getName();

    /** Reads the correlation ID assigned to the current request. */
    public static UUID id(HttpServletRequest request) {
        return (UUID) request.getAttribute(ATTRIBUTE);
    }

    /** Assigns a correlation ID before passing the request to downstream handlers. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        UUID id = UUID.randomUUID();
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader("X-Request-Id", id.toString());
        chain.doFilter(request, response);
    }
}
