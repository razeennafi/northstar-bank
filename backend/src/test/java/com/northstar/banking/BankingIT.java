/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: BankingIT.java
 * Description: Tests committed banking workflows, concurrency, recovery, and database constraints.
 * Project version: 0.1.0
 */

package com.northstar.banking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.northstar.banking.common.BankingException;
import com.northstar.banking.transfers.TransferRequest;
import com.northstar.banking.transfers.TransferService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies authorised customer operations and committed financial state in real PostgreSQL. */
@SpringBootTest(properties = "bank.demo-funding-enabled=true")
@AutoConfigureMockMvc
@Testcontainers
class BankingIT {
    @Container
    static final PostgreSQLContainer database = new PostgreSQLContainer("postgres:17.12-alpine");

    /** Connects Spring to the temporary PostgreSQL instance assigned to this test class. */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", database::getJdbcUrl);
        properties.add("spring.datasource.username", database::getUsername);
        properties.add("spring.datasource.password", database::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcClient jdbc;
    @Autowired TransferService transfers;
    @Autowired PlatformTransactionManager transactionManager;

    /** Verifies that account ownership gates both reads and demo funding, and that rejected access is audited. */
    @Test
    void onlyTheOwnerCanReadOrFundAnAccount() throws Exception {
        var owner = customer();
        var stranger = customer();
        UUID id = account(owner, "CHEQUING");
        mvc.perform(get("/api/v1/accounts/{id}", id).session(owner.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value("0.00"));
        mvc.perform(get("/api/v1/accounts/{id}", id).session(stranger.session()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACCOUNT_UNAVAILABLE"));
        mvc.perform(get("/api/v1/accounts").session(stranger.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accounts").isEmpty());
        mvc.perform(get("/api/v1/audit").session(stranger.session())).andExpect(status().isForbidden());
        fund(stranger, id).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
        assertThat(jdbc.sql("SELECT count(*) FROM ledger_entries WHERE account_id = ?")
                .param(id).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE actor_id = ? AND action = 'ACCOUNT_ACCESS'")
                .param(stranger.id()).query(Long.class).single()).isEqualTo(2);
    }

    /**
     * Verifies that the account overview returns its JDBC connection to the pool after each read.
     * The test funds a chequing account, requests its overview twelve times in sequence, and checks
     * that each response succeeds and still shows CAD 500. Twelve reads exceed the eight pool slots.
     */
    @Test
    void repeatedBalanceOverviewsReturnConnectionsToThePool() throws Exception {
        var owner = customer();
        UUID id = account(owner, "CHEQUING");
        fund(owner, id).andExpect(status().isCreated());
        for (int request = 0; request < 12; request++) {
            mvc.perform(get("/api/v1/accounts").session(owner.session()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalBalance").value("500.00"));
        }
    }

    /** Checks that demo funding creates one balanced posting and that PostgreSQL rejects a second grant or a ledger rewrite. */
    @Test
    void theDemoGrantPostsOneBalancedPairAndCannotBeRepeatedOrRewritten() throws Exception {
        var owner = customer();
        UUID id = account(owner, "SAVINGS");
        var posted = body(fund(owner, id).andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("500.00")).andExpect(jsonPath("$.kind").value("DEMO_FUNDING")));
        UUID posting = UUID.fromString(posted.path("id").asString());
        fund(owner, id).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/accounts/{id}", id).session(owner.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value("500.00"));
        assertThat(jdbc.sql("SELECT direction FROM ledger_entries WHERE transfer_id = ? ORDER BY direction")
                .param(posting).query(String.class).list()).containsExactly("CREDIT", "DEBIT");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM ledger_entries WHERE transfer_id = ?").param(posting).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO accounts (id, customer_id, account_type, currency, status, created_at)
                VALUES (?, ?, 'CHEQUING', 'CAD', 'ACTIVE', now())
                """).param(UUID.randomUUID()).param(UUID.randomUUID()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Verifies that a retry returns the original receipt and that reusing its key with changed details is rejected. */
    @Test
    void theSameKeyReplaysOneCommittedTransferAndRejectsADifferentPayload() throws Exception {
        var owner = customer();
        var other = customer();
        UUID source = account(owner, "CHEQUING");
        UUID destination = account(other, "SAVINGS");
        fund(owner, source).andExpect(status().isCreated());
        UUID key = UUID.randomUUID();

        var posted = body(transfer(owner, source, destination, "25.50", key).andExpect(status().isCreated()));
        var replay = body(transfer(owner, source, destination, "25.5", key).andExpect(status().isOk()));
        assertThat(replay).isEqualTo(posted);
        transfer(owner, source, destination, "26", key).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        transfer(other, source, destination, "25", UUID.randomUUID()).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/transfers/by-key/{key}", key).session(owner.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(posted.path("id").asString()));
        mvc.perform(get("/api/v1/transfers/by-key/{key}", key).session(other.session()))
                .andExpect(status().isNotFound());
        assertThat(balance(owner, source)).isEqualTo("474.50");
        assertThat(balance(other, destination)).isEqualTo("25.50");
        assertThat(jdbc.sql("SELECT count(*) FROM ledger_entries WHERE transfer_id = ?")
                .param(UUID.fromString(posted.path("id").asString())).query(Long.class).single()).isEqualTo(2);
    }

    /** Checks that validation and insufficient funds leave no committed posting or idempotency claim. */
    @Test
    void insufficientFundsAndInvalidAmountsLeaveNoTransferOrClaim() throws Exception {
        var owner = customer();
        UUID source = account(owner, "CHEQUING");
        UUID destination = account(owner, "SAVINGS");
        UUID key = UUID.randomUUID();
        transfer(owner, source, destination, "5.001", key).andExpect(status().isBadRequest());
        transfer(owner, source, destination, "5.00", key).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertThat(jdbc.sql("SELECT count(*) FROM transfer_requests WHERE request_key = ?")
                .param(key).query(Long.class).single()).isZero();
        assertThat(balance(owner, source)).isEqualTo("0.00");
        assertThat(balance(owner, destination)).isEqualTo("0.00");
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE actor_id = ? AND action = 'TRANSFER'")
                .param(owner.id()).query(Long.class).single()).isEqualTo(2);
    }

    /** Forces a credit write to fail after the debit so the test can check rollback and retry the original request. */
    @Test
    void aDatabaseFailureAfterTheDebitRollsBackBothEntriesAndAllowsSafeRetry() throws Exception {
        var owner = customer();
        UUID source = account(owner, "CHEQUING");
        UUID destination = account(owner, "SAVINGS");
        fund(owner, source).andExpect(status().isCreated());
        UUID key = UUID.randomUUID();
        // Fail precisely the test account's credit after the debit INSERT already ran.
        jdbc.sql("""
                CREATE FUNCTION reject_test_credit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.account_id = '%s'::uuid AND NEW.direction = 'CREDIT' THEN
                        RAISE EXCEPTION 'Test credit rejected' USING ERRCODE = '23514';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """.formatted(destination)).update();
        jdbc.sql("""
                CREATE TRIGGER reject_test_credit BEFORE INSERT ON ledger_entries
                FOR EACH ROW EXECUTE FUNCTION reject_test_credit()
                """).update();
        try {
            transfer(owner, source, destination, "100", key).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("UNEXPECTED_FAILURE"));
            assertThat(balance(owner, source)).isEqualTo("500.00");
            assertThat(balance(owner, destination)).isEqualTo("0.00");
            assertThat(jdbc.sql("SELECT count(*) FROM transfers WHERE destination_account_id = ?")
                    .param(destination).query(Long.class).single()).isZero();
            assertThat(jdbc.sql("SELECT count(*) FROM transfer_requests WHERE request_key = ?")
                    .param(key).query(Long.class).single()).isZero();
        } finally {
            jdbc.sql("DROP TRIGGER reject_test_credit ON ledger_entries").update();
            jdbc.sql("DROP FUNCTION reject_test_credit()").update();
        }
        transfer(owner, source, destination, "100", key).andExpect(status().isCreated());
        assertThat(balance(owner, source)).isEqualTo("400.00");
        assertThat(balance(owner, destination)).isEqualTo("100.00");
    }

    /** Attempts to commit an incomplete posting directly in PostgreSQL to exercise the deferred constraint independently of the service. */
    @Test
    void postgresRejectsACommitThatContainsOnlyOneSideOfAPosting() throws Exception {
        var owner = customer();
        UUID source = account(owner, "CHEQUING");
        UUID destination = account(owner, "SAVINGS");
        UUID posting = UUID.randomUUID();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            jdbc.sql("""
                    INSERT INTO transfers(id, requester_id, source_account_id, destination_account_id,
                                          amount, currency, kind, status, created_at, completed_at)
                    VALUES (?, ?, ?, ?, 1.00, 'CAD', 'TRANSFER', 'COMPLETED', now(), now())
                    """).param(posting).param(owner.id()).param(source).param(destination).update();
            jdbc.sql("""
                    INSERT INTO ledger_entries(id, transfer_id, account_id, direction, amount, created_at)
                    VALUES (?, ?, ?, 'DEBIT', 1.00, now())
                    """).param(UUID.randomUUID()).param(posting).param(source).update();
        })).hasStackTraceContaining("Posting requires matching debit and credit entries");
        assertThat(jdbc.sql("SELECT count(*) FROM transfers WHERE id = ?")
                .param(posting).query(Long.class).single()).isZero();
        assertThat(balance(owner, source)).isEqualTo("0.00");
    }

    /** Competes for the same source balance to check that only an affordable withdrawal commits. */
    @Test
    void concurrentWithdrawalsCannotSpendMoreThanTheOpeningBalance() throws Exception {
        var owner = customer();
        UUID source = account(owner, "CHEQUING");
        UUID first = account(owner, "SAVINGS");
        UUID second = account(owner, "SAVINGS");
        fund(owner, source).andExpect(status().isCreated());
        var codes = race(owner.id(),
                new TransferRequest(source, first, new BigDecimal("400"), "CAD"), UUID.randomUUID(),
                new TransferRequest(source, second, new BigDecimal("300"), "CAD"), UUID.randomUUID());
        assertThat(codes).containsExactlyInAnyOrder("POSTED", "INSUFFICIENT_FUNDS");
        assertThat(balance(owner, source)).isIn("100.00", "200.00");
        assertThat(jdbc.sql("SELECT count(*) FROM transfers WHERE source_account_id = ?")
                .param(source).query(Long.class).single()).isEqualTo(1);
        assertThat(new BigDecimal(balance(owner, source)).add(new BigDecimal(balance(owner, first)))
                .add(new BigDecimal(balance(owner, second)))).isEqualByComparingTo("500.00");
    }

    /** Exercises simultaneous retries and opposite-direction transfers to check duplicate handling and consistent lock ordering. */
    @Test
    void concurrentDuplicateAndOppositeDirectionRequestsCompleteWithoutDoublePosting() throws Exception {
        var owner = customer();
        UUID left = account(owner, "CHEQUING");
        UUID right = account(owner, "SAVINGS");
        fund(owner, left).andExpect(status().isCreated());
        fund(owner, right).andExpect(status().isCreated());
        var forward = new TransferRequest(left, right, new BigDecimal("10"), "CAD");
        var reverse = new TransferRequest(right, left, new BigDecimal("20"), "CAD");

        UUID duplicateKey = UUID.randomUUID();
        assertThat(race(owner.id(), forward, duplicateKey, forward, duplicateKey))
                .containsExactlyInAnyOrder("POSTED", "REPLAYED");
        assertThat(race(owner.id(), forward, UUID.randomUUID(), reverse, UUID.randomUUID()))
                .containsExactly("POSTED", "POSTED");
        assertThat(balance(owner, left)).isEqualTo("500.00");
        assertThat(balance(owner, right)).isEqualTo("500.00");
    }

    /** Holds a competing account lock to verify the retryable HTTP response and prove that a timed-out request leaves its key reusable. */
    @Test
    void aLockTimeoutReturns503AndTheSameKeyCanRetryAfterRelease() throws Exception {
        var owner = customer();
        UUID source = account(owner, "CHEQUING");
        UUID destination = account(owner, "SAVINGS");
        fund(owner, source).andExpect(status().isCreated());
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        UUID key = UUID.randomUUID();
        try (var worker = Executors.newSingleThreadExecutor()) {
            var lock = worker.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                jdbc.sql("SELECT id FROM accounts WHERE id = ? FOR UPDATE").param(source).query(UUID.class).single();
                locked.countDown();
                try {
                    if (!release.await(9, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test timed out waiting to release the account lock");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Lock test interrupted", exception);
                }
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                transfer(owner, source, destination, "25", key).andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"));
                assertThat(jdbc.sql("SELECT count(*) FROM transfer_requests WHERE request_key = ?")
                        .param(key).query(Long.class).single()).isZero();
            } finally {
                release.countDown();
            }
            lock.get(5, TimeUnit.SECONDS);
        }
        transfer(owner, source, destination, "25", key).andExpect(status().isCreated());
        assertThat(balance(owner, source)).isEqualTo("475.00");
    }

    /** Compares paginated activity, statement totals, and exported files with the same committed ledger activity. */
    @Test
    void ledgerHistoryIsPagedAndStatementExportsMatchCommittedTotals() throws Exception {
        var owner = customer();
        var stranger = customer();
        UUID source = account(owner, "CHEQUING");
        UUID destination = account(owner, "SAVINGS");
        fund(owner, source).andExpect(status().isCreated());
        transfer(owner, source, destination, "25.75", UUID.randomUUID()).andExpect(status().isCreated());
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String tomorrow = LocalDate.parse(today).plusDays(1).toString();

        var history = "/api/v1/accounts/{id}/transactions";
        mvc.perform(get(history, source).session(owner.session()).param("size", "1").param("from", today))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get(history, source).session(owner.session()).param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get(history, source).session(owner.session()).param("sort", "ASC"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].type").value("DEMO_FUNDING"));
        mvc.perform(get(history, source).session(owner.session()).param("size", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(history, source).session(stranger.session())).andExpect(status().isNotFound());

        var statement = "/api/v1/statements";
        mvc.perform(get(statement).session(owner.session()).param("accountId", source.toString())
                        .param("from", today).param("to", today))
                .andExpect(status().isOk()).andExpect(jsonPath("$.openingBalance").value("0.00"))
                .andExpect(jsonPath("$.credits").value("500.00")).andExpect(jsonPath("$.debits").value("25.75"))
                .andExpect(jsonPath("$.closingBalance").value("474.25"));
        mvc.perform(get(statement).session(owner.session()).param("accountId", source.toString())
                        .param("from", tomorrow).param("to", tomorrow))
                .andExpect(status().isOk()).andExpect(jsonPath("$.openingBalance").value("474.25"))
                .andExpect(jsonPath("$.entries").value(0));
        mvc.perform(get(statement).session(owner.session()).param("accountId", source.toString())
                        .param("from", tomorrow).param("to", today))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        mvc.perform(get(statement + "/download").session(stranger.session()).param("accountId", source.toString())
                        .param("from", today).param("to", today))
                .andExpect(status().isNotFound());

        assertThat(new String(download(owner, source, today, "CSV"), java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"Closing balance\",\"474.25\"", "\"DEBIT\",\"25.75\",\"TRANSFER\",\"COMPLETED\"");
        try (var pdf = Loader.loadPDF(download(owner, source, today, "PDF"))) {
            assertThat(pdf.getNumberOfPages()).isOne();
            assertThat(new PDFTextStripper().getText(pdf)).contains("Closing balance 474.25", "25.75", "Northstar Bank");
        }
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE actor_id = ? AND action = 'STATEMENT_GENERATED'")
                .param(owner.id()).query(Long.class).single()).isEqualTo(2);
    }

    private byte[] download(CustomerSession owner, UUID account, String day, String format) throws Exception {
        var result = mvc.perform(get("/api/v1/statements/download").session(owner.session())
                        .param("accountId", account.toString()).param("from", day).param("to", day).param("format", format))
                .andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    /** Starts two transfer workers together and collects both outcomes within a fixed deadline. */
    private java.util.List<String> race(UUID actor, TransferRequest first, UUID firstKey,
                                       TransferRequest second, UUID secondKey) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> concurrentTransfer(ready, start, actor, firstKey, first));
            var b = workers.submit(() -> concurrentTransfer(ready, start, actor, secondKey, second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return java.util.List.of(a.get(12, TimeUnit.SECONDS), b.get(12, TimeUnit.SECONDS));
        }
    }

    /** Waits for the shared start signal and records the worker’s business outcome for assertions. */
    private String concurrentTransfer(CountDownLatch ready, CountDownLatch start, UUID actor,
                                      UUID key, TransferRequest request) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test did not start");
        }
        try {
            return transfers.transfer(actor, key, request, UUID.randomUUID()).replayed() ? "REPLAYED" : "POSTED";
        } catch (BankingException rejection) {
            return rejection.code();
        }
    }

    /** Submits a transfer using the fixture’s authenticated session, CSRF token, and supplied retry key. */
    private ResultActions transfer(CustomerSession customer, UUID from, UUID to, String amount, UUID key) throws Exception {
        return mvc.perform(post("/api/v1/transfers").session(customer.session())
                .header("X-CSRF-TOKEN", customer.csrf()).header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "sourceAccountId", from, "destinationAccountId", to, "amount", amount, "currency", "CAD"))));
    }

    /** Reads the balance through the same owner-scoped endpoint the browser uses. */
    private String balance(CustomerSession customer, UUID id) throws Exception {
        return body(mvc.perform(get("/api/v1/accounts/{id}", id).session(customer.session()))
                .andExpect(status().isOk())).path("balance").asString();
    }

    /** Registers and signs in a fresh customer to isolate each test’s data. */
    private CustomerSession customer() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String password = "example-study-passphrase";
        var session = new MockHttpSession();
        var guest = new CustomerSession(null, session, csrf(session));
        var registration = body(mutate(guest, "/api/v1/auth/register",
                """
                {"fullName":"Demo Student","email":"%s","password":"%s"}
                """.formatted(email, password))
                .andExpect(status().isCreated()));
        var result = mutate(guest, "/api/v1/auth/login",
                """
                {"email":"%s","password":"%s"}
                """.formatted(email, password))
                .andExpect(status().isOk()).andReturn();
        var authenticated = (MockHttpSession) result.getRequest().getSession(false);
        return new CustomerSession(UUID.fromString(registration.path("id").asString()), authenticated, csrf(authenticated));
    }

    /** Fetches the token associated with the current test session. */
    private String csrf(MockHttpSession session) throws Exception {
        return body(mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk()))
                .path("token").asString();
    }

    /** Opens an account through the API and returns its ID for later requests. */
    private UUID account(CustomerSession customer, String type) throws Exception {
        return UUID.fromString(body(mutate(customer, "/api/v1/accounts", json.writeValueAsString(java.util.Map.of("type", type)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.balance").value("0.00"))).path("id").asString());
    }

    /** Applies simulated funds through the authenticated demo endpoint. */
    private ResultActions fund(CustomerSession customer, UUID id) throws Exception {
        return mutate(customer, "/api/v1/demo/funding", json.writeValueAsString(java.util.Map.of("accountId", id)));
    }

    /** Builds a JSON POST with the session and CSRF credentials attached. */
    private ResultActions mutate(CustomerSession customer, String path, String body) throws Exception {
        return mvc.perform(post(path).session(customer.session()).header("X-CSRF-TOKEN", customer.csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Parses a completed response for field-level assertions. */
    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private record CustomerSession(UUID id, MockHttpSession session, String csrf) {
    }
}
