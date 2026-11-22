/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: AuthenticationIT.java
 * Description: Tests session security, login throttling, and API documentation.
 * Project version: 0.1.0
 */

package com.northstar.banking;

import com.northstar.banking.common.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import com.northstar.banking.accounts.DemoFundingService;
import com.northstar.banking.common.BankingException;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real Spring filter chain and a Flyway-managed PostgreSQL
 * database. Requests have their own transaction boundaries so observations
 * made after the response see committed rows only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthenticationIT {
    @Container
    static final PostgreSQLContainer database = new PostgreSQLContainer("postgres:17.12-alpine");

    /** Supplies the temporary database connection settings before Spring starts. */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", database::getJdbcUrl);
        properties.add("spring.datasource.username", database::getUsername);
        properties.add("spring.datasource.password", database::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcClient jdbc;
    @Autowired DemoFundingService funding;

    /** Exercises the full session lifecycle and verifies credential storage, session rotation, and CSRF invalidation. */
    @Test
    void customerCanRegisterSignInAndSignOutWithCsrfAndSessionRotation() throws Exception {
        var guest = new MockHttpSession();
        String csrf = token(guest);
        mvc.perform(post("/api/v1/auth/register").session(guest).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("Student@Example.test")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("student@example.test"))
                .andExpect(jsonPath("$.password").doesNotExist());

        String hash = jdbc.sql("""
                SELECT password_hash FROM credentials
                JOIN customers ON customers.id = credentials.customer_id WHERE email = ?
                """).param("student@example.test").query(String.class).single();
        assertThat(hash).isNotEqualTo("example-study-passphrase").hasSizeGreaterThan(32);

        String beforeLogin = guest.getId();
        MvcResult login = mvc.perform(post("/api/v1/auth/login").session(guest).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON).content(login("student@example.test", "example-study-passphrase")))
                .andExpect(status().isOk())
                .andReturn();
        var authenticated = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(authenticated).isNotNull();
        assertThat(authenticated.getId()).isNotEqualTo(beforeLogin);
        mvc.perform(get("/api/v1/auth/me").session(authenticated)).andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Demo Student"));

        mvc.perform(post("/api/v1/auth/logout").session(authenticated).header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/v1/auth/logout").session(authenticated).header("X-CSRF-TOKEN", token(authenticated)))
                .andExpect(status().isNoContent());
        assertThat(authenticated.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    /** Checks that duplicate registration, malformed JSON, and missing CSRF produce the intended safe HTTP errors. */
    @Test
    void duplicateEmailInvalidBodyAndMissingCsrfGetSafeErrors() throws Exception {
        var guest = new MockHttpSession();
        String csrf = token(guest);
        mvc.perform(post("/api/v1/auth/register").session(guest).contentType(MediaType.APPLICATION_JSON)
                        .content(registration("second@example.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/v1/auth/register").session(guest).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON).content(registration("second@example.test")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register").session(guest).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON).content(registration("SECOND@example.test")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_UNAVAILABLE"));
        for (String body : new String[]{"{", "{}"}) {
            mvc.perform(post("/api/v1/auth/register").session(guest).header("X-CSRF-TOKEN", csrf)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.requestId").isString());
        }
    }

    /** Checks that a failed login gives a generic message while retaining a durable rejection audit event. */
    @Test
    void unknownCredentialsProduceGenericErrorAndCommittedAuditEvent() throws Exception {
        var guest = new MockHttpSession();
        var result = mvc.perform(post("/api/v1/auth/login").session(guest).header("X-CSRF-TOKEN", token(guest))
                        .contentType(MediaType.APPLICATION_JSON).content(login("absent@example.test", "invalid-passphrase")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."))
                .andReturn();
        ApiError error = json.readValue(result.getResponse().getContentAsString(), ApiError.class);
        assertThat(jdbc.sql("SELECT outcome FROM audit_events WHERE request_id = ?")
                .param(error.requestId()).query(String.class).single()).isEqualTo("REJECTED");
    }

    /** Repeats unsuccessful login attempts until the limiter returns a retry window. */
    @Test
    void failedLoginAttemptsAreLimitedAndReturnRetryAfter() throws Exception {
        var session = new MockHttpSession();
        String csrf = token(session);
        String details = login("rate-limit@example.test", "invalid-passphrase");
        for (int attempt = 0; attempt < 8; attempt++) {
            mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN", csrf)
                            .contentType(MediaType.APPLICATION_JSON).content(details))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON).content(details))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("LOGIN_THROTTLED"));
    }

    /** Verifies that deployments must explicitly enable simulated grants. */
    @Test
    void demoFundingDefaultsToDisabled() {
        assertThatThrownBy(() -> funding.fund(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(BankingException.class)
                .extracting(error -> ((BankingException) error).code()).isEqualTo("DEMO_UNAVAILABLE");
    }

    /** Checks that the generated contract includes filter-based logout and the transfer and statement routes. */
    @Test
    void openApiIncludesTheSpringSecurityLogoutFilterAndTransferContract() throws Exception {
        var result = mvc.perform(get("/api/v1/openapi")).andExpect(status().isOk()).andReturn();
        var document = json.readTree(result.getResponse().getContentAsString());
        assertThat(document.at("/components/securitySchemes/customerSession/name").asString()).isEqualTo("NORTHSTAR_SESSION");
        var paths = document.path("paths");
        assertThat(paths.path("/api/v1/auth/logout").path("post").path("responses").has("204")).isTrue();
        assertThat(paths.path("/api/v1/transfers").path("post").path("responses").has("201")).isTrue();
        assertThat(paths.path("/api/v1/statements/download").has("get")).isTrue();
    }

    /** Obtains a CSRF token for this fixture’s session. */
    private String token(MockHttpSession session) throws Exception {
        var response = mvc.perform(get("/api/v1/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("token").asString();
    }

    /** Builds a valid registration body around the supplied email. */
    private String registration(String email) {
        return """
                {"fullName":"Demo Student","email":"%s","password":"example-study-passphrase"}
                """.formatted(email);
    }

    /** Builds a login body with credentials chosen by the scenario. */
    private String login(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }
}
