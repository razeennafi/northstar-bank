/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: AuthRequests.java
 * Description: Defines bounded JSON inputs for the authentication endpoints.
 * Project version: 0.1.0
 */

package com.northstar.banking.authentication;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Defines bounded JSON inputs for the authentication endpoints. */
final class AuthRequests {
    private AuthRequests() {
    }

    record Register(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 128) String password) {
    }

    record Login(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {
    }

    record Csrf(String headerName, String token) {
    }
}
