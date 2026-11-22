/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: Customer.java
 * Description: Maps customer profile fields to PostgreSQL.
 * Project version: 0.1.0
 */

package com.northstar.banking.customers;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps customer profile fields to PostgreSQL. JPA's @Entity marks the class for
 * Hibernate and @Id identifies a row independently of fields like email.
 * The credential module owns secrets separately and HTTP uses CustomerProfile.
 */
@Entity
@Table(name = "customers")
public class Customer {
    @Id
    private UUID id;
    private String fullName;
    private String email;
    private String status;
    private Instant createdAt;

    protected Customer() {
        // Hibernate needs a constructor to materialize rows.
    }

    CustomerProfile profile() {
        return new CustomerProfile(id, fullName, email, createdAt);
    }

    boolean active() {
        return "ACTIVE".equals(status);
    }
}
