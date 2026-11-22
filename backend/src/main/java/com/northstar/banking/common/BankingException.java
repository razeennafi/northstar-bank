/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: BankingException.java
 * Description: Carries an intentionally public error code and message across service boundaries.
 * Project version: 0.1.0
 */

package com.northstar.banking.common;

import org.springframework.http.HttpStatus;

/** Carries an intentionally public error code and message across service boundaries. */
public class BankingException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public BankingException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
