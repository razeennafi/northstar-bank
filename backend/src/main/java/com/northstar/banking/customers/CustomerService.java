/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: CustomerService.java
 * Description: Owns profile persistence and exposes active identities to authentication.
 * Project version: 0.1.0
 */

package com.northstar.banking.customers;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import com.northstar.banking.common.BankingException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Owns profile persistence and exposes active identities to authentication. */
@Service
public class CustomerService {
    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    /** Normalizes profile details and delegates email uniqueness to PostgreSQL. */
    public CustomerProfile create(String name, String email) {
        var profile = new CustomerProfile(UUID.randomUUID(), name.strip(), canonicalEmail(email), Instant.now());
        // A normal duplicate returns zero rows from PostgreSQL. Constraint
        // exceptions could include the submitted email in JDBC diagnostics.
        if (customers.insertIfEmailAvailable(profile.id(), profile.fullName(), profile.email(), profile.createdAt()) == 0) {
            throw new BankingException(HttpStatus.CONFLICT, "EMAIL_UNAVAILABLE", "This email is already in use.");
        }
        return profile;
    }

    /** Uses a normalized email to resolve an active login profile. */
    public Optional<CustomerProfile> findActiveByEmail(String email) {
        return customers.findByEmailAndStatus(canonicalEmail(email), "ACTIVE").map(Customer::profile);
    }

    /** Looks up the active customer behind a session identity. */
    public Optional<CustomerProfile> findActive(UUID id) {
        return customers.findById(id).filter(Customer::active).map(Customer::profile);
    }

    /** Locks the active customer row for an operation that must serialize per customer. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockActive(UUID id) {
        customers.findActiveForUpdate(id).orElseThrow(() ->
                new BankingException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Sign in to continue."));
    }

    /** Applies consistent email normalization before lookup or storage. */
    private String canonicalEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
