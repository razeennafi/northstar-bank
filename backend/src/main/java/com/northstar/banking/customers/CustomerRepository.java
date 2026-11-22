/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: CustomerRepository.java
 * Description: Spring Data generates the implementation of this interface.
 * Project version: 0.1.0
 */

package com.northstar.banking.customers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data generates the implementation of this interface. The generic type
 * parameters tell it which entity and identifier Hibernate should use.
 */
interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByEmailAndStatus(String email, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id and c.status = 'ACTIVE'")
    Optional<Customer> findActiveForUpdate(UUID id);

    @Modifying
    @Query(value = """
            INSERT INTO customers(id, full_name, email, status, created_at)
            VALUES (:id, :fullName, :email, 'ACTIVE', :createdAt)
            ON CONFLICT ON CONSTRAINT customers_email_unique DO NOTHING
            """, nativeQuery = true)
    int insertIfEmailAvailable(UUID id, String fullName, String email, Instant createdAt);
}
