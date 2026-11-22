/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: AccountRepository.java
 * Description: Spring Data repository for owner-scoped queries and explicit posting locks.
 * Project version: 0.1.0
 */

package com.northstar.banking.accounts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Spring Data repository for owner-scoped queries and explicit posting locks. */
interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByCustomerIdOrderByCreatedAtAscIdAsc(UUID customerId);
    Optional<Account> findByIdAndCustomerId(UUID id, UUID customerId);
    long countByCustomerId(UUID customerId);

    /** Acquires a pessimistic lock on an account before checking or changing its ledger balance. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findForPosting(UUID id);
}
