/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: AuthController.java
 * Description: Maps JSON and session actions to the authentication service.
 * Project version: 0.1.0
 */

package com.northstar.banking.authentication;

import java.util.List;
import com.northstar.banking.common.RequestIdFilter;
import com.northstar.banking.customers.CustomerProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Maps JSON and session actions to the authentication service. RestController
 * serializes DTOs as HTTP bodies, and @Valid checks input before business code runs.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationService auth;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository tokens;
    private final LoginRateLimiter limiter;

    public AuthController(AuthenticationService auth, SecurityContextRepository contexts, CsrfTokenRepository tokens,
                          LoginRateLimiter limiter) {
        this.auth = auth;
        this.contexts = contexts;
        this.tokens = tokens;
        this.limiter = limiter;
    }

    /** Exposes the current session’s CSRF header name and token to the frontend. */
    @GetMapping("/csrf")
    AuthRequests.Csrf csrf(CsrfToken token) {
        return new AuthRequests.Csrf(token.getHeaderName(), token.getToken());
    }

    /** Validates registration input and passes the request ID into the creation workflow. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    CustomerProfile register(@Valid @RequestBody AuthRequests.Register body, HttpServletRequest request) {
        return auth.register(body, RequestIdFilter.id(request));
    }

    /**
     * Establishes a server-side session after credential verification. Rotating
     * the ID prevents session fixation. The browser fetches a new CSRF token
     * after this transition, and the stored principal has only the customer ID.
     */
    @PostMapping("/login")
    CustomerProfile login(@Valid @RequestBody AuthRequests.Login body, HttpServletRequest request,
                          HttpServletResponse response) {
        limiter.check(body.email(), request.getRemoteAddr());
        CustomerProfile profile = auth.authenticate(body, RequestIdFilter.id(request));
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        tokens.saveToken(null, request, response);
        var principal = new CustomerIdentity(profile.id());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return profile;
    }

    /** Returns the profile represented by the authenticated session. */
    @GetMapping("/me")
    CustomerProfile me(@AuthenticationPrincipal CustomerIdentity principal) {
        return auth.profile(principal.id());
    }
}
