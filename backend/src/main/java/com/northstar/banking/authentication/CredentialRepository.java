/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: CredentialRepository.java
 * Description: Keeps credential reads and writes inside the authentication module.
 * Project version: 0.1.0
 */

package com.northstar.banking.authentication;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Keeps credential reads and writes inside the authentication module. */
interface CredentialRepository extends JpaRepository<Credential, UUID> {
}
