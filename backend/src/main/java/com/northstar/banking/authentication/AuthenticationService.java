/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: AuthenticationService.java
 * Description: Coordinates customer registration and password verification.
 * Project version: 0.1.0
 */

package com.northstar.banking.authentication;

import java.util.UUID;
import com.northstar.banking.audit.AuditService;
import com.northstar.banking.common.BankingException;
import com.northstar.banking.customers.CustomerProfile;
import com.northstar.banking.customers.CustomerService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates customer registration and password verification. Spring injects
 * the collaborators through the constructor and supplies a transactional proxy.
 */
@Service
public class AuthenticationService {
    private final CredentialRepository credentials;
    private final CustomerService customers;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final String missingCustomerHash;

    public AuthenticationService(CredentialRepository credentials, CustomerService customers,
                                 PasswordEncoder encoder, AuditService audit) {
        this.credentials = credentials;
        this.customers = customers;
        this.encoder = encoder;
        this.audit = audit;
        this.missingCustomerHash = encoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Commits a profile, password hash and registration audit as one unit.
     * The database arbitrates concurrent registrations for the same email.
     *
     * @param request validated registration fields
     * @param requestId request correlation identifier
     * @return public details for the newly committed profile
     * @throws BankingException if the email is already in use
     */
    @Transactional
    public CustomerProfile register(AuthRequests.Register request, UUID requestId) {
        String hash = encoder.encode(request.password());
        CustomerProfile customer = customers.create(request.fullName(), request.email());
        credentials.save(new Credential(customer.id(), hash));
        audit.success(customer.id(), "REGISTRATION", "CUSTOMER", customer.id(), requestId);
        return customer;
    }

    /**
     * Verifies credentials before the controller creates an authenticated session.
     * A missing user still incurs password verification work to reduce timing clues.
     *
     * @param request validated email and password
     * @param requestId request correlation identifier
     * @return public profile for the authenticated customer
     * @throws BankingException for invalid credentials
     */
    public CustomerProfile authenticate(AuthRequests.Login request, UUID requestId) {
        CustomerProfile profile = customers.findActiveByEmail(request.email()).orElse(null);
        String storedHash = profile == null ? null
                : credentials.findById(profile.id()).map(Credential::hash).orElse(null);
        boolean valid = encoder.matches(request.password(), storedHash == null ? missingCustomerHash : storedHash);
        if (!valid || profile == null || storedHash == null) {
            audit.rejected(null, "LOGIN", "SESSION", null, requestId);
            throw new BankingException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
        }
        audit.success(profile.id(), "LOGIN", "SESSION", null, requestId);
        return profile;
    }

    /** Resolves the active customer behind an authenticated session. */
    public CustomerProfile profile(UUID customerId) {
        return customers.findActive(customerId).orElseThrow(() ->
                new BankingException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Sign in to continue."));
    }
}
