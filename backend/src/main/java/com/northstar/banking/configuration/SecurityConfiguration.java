/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: SecurityConfiguration.java
 * Description: Defines the HTTP boundary and session policy.
 * Project version: 0.1.0
 */

package com.northstar.banking.configuration;

import java.io.IOException;
import com.northstar.banking.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Defines the HTTP boundary and session policy. The reverse proxies keep
 * browser requests same-origin, and Spring Security verifies the session's
 * CSRF token on mutations, including login and logout.
 */
@Configuration
public class SecurityConfiguration {

    /** Defines the password hashing configuration used by registration and login. */
    @Bean
    PasswordEncoder passwordEncoder() {
        // PBKDF2 uses a random per-password salt and work factor to slow guessing.
        return new Pbkdf2PasswordEncoder("", 16, 600_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
    }

    /** Keeps the expected CSRF token with the server-side session. */
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    /** Persists the authenticated security context in the session. */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Spring injects the filter-chain builder used to authorize incoming requests.
     * Requests outside the explicit allowlist are denied.
     *
     * @param http Spring Security's servlet configuration builder
     * @param contexts server-side authentication storage used by the login controller
     * @param tokens server-side CSRF storage
     * @param mapper JSON serializer for errors raised before MVC
     * @return the configured filter chain for incoming requests
     * @throws Exception if Spring cannot build the security configuration
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
                                            CsrfTokenRepository tokens, ObjectMapper mapper) throws Exception {
        return http
                .securityContext(security -> security.securityContextRepository(contexts))
                .csrf(csrf -> csrf.csrfTokenRepository(tokens))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(mapper, request, response, 401, "AUTHENTICATION_REQUIRED", "Sign in to continue."))
                        .accessDeniedHandler((request, response, exception) -> {
                            if (exception instanceof CsrfException) {
                                writeError(mapper, request, response, 403, "CSRF_INVALID", "Refresh the page and try again.");
                            } else {
                                writeError(mapper, request, response, 403, "ACCESS_DENIED", "Access is unavailable.");
                            }
                        }))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .deleteCookies("NORTHSTAR_SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/actuator/health/readiness", "/api/v1/auth/csrf", "/api/v1/openapi").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").hasRole("CUSTOMER")
                        .requestMatchers("/api/v1/accounts", "/api/v1/accounts/**", "/api/v1/demo/funding").hasRole("CUSTOMER")
                        .requestMatchers("/api/v1/transfers", "/api/v1/transfers/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/statements", "/api/v1/statements/download").hasRole("CUSTOMER")
                        .anyRequest().denyAll())
                .build();
    }

    /** Writes the safe JSON error contract when Spring Security rejects a request before MVC. */
    private void writeError(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                            int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ApiError.of(code, message, request));
    }
}
