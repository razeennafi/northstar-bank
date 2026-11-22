/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: BankingApplication.java
 * Description: Starts the Northstar modular monolith.
 * Project version: 0.1.0
 */

package com.northstar.banking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Starts the Northstar modular monolith. The root package lets Spring discover
 * configuration and banking modules within this process. The customer
 * authentication module owns credentials, so Boot's generated development
 * user is disabled.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BankingApplication {

    /** Starts the Spring container and its embedded web server. */
    public static void main(String[] args) {
        SpringApplication.run(BankingApplication.class, args);
    }
}
