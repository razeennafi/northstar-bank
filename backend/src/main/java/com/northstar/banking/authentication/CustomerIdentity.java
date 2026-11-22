/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: CustomerIdentity.java
 * Description: The only customer data retained in Spring Security's session principal.
 * Project version: 0.1.0
 */

package com.northstar.banking.authentication;

import java.io.Serializable;
import java.util.UUID;

/** The only customer data retained in Spring Security's session principal. */
public record CustomerIdentity(UUID id) implements Serializable {
}
