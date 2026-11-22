/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: TransferRequestTest.java
 * Description: Tests transfer amount normalization and rejection rules.
 * Project version: 0.1.0
 */

package com.northstar.banking.transfers;

import java.math.BigDecimal;
import java.util.UUID;
import com.northstar.banking.common.BankingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferRequestTest {
    private final UUID source = UUID.randomUUID();
    private final UUID destination = UUID.randomUUID();

    /** Rejects invalid decimal values before PostgreSQL can round or reject a posting. */
    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "10.001", "10.000", "100000000000000000.00"})
    void rejectsAmountsPostgresCouldRoundOrReject(String amount) {
        var request = new TransferRequest(source, destination, new BigDecimal(amount), "CAD");
        assertThatThrownBy(request::canonicalAmount).isInstanceOf(BankingException.class)
                .extracting(error -> ((BankingException) error).code()).isEqualTo("INVALID_AMOUNT");
    }

    /** Checks that equivalent CAD amounts normalize to the same representation for duplicate detection. */
    @Test
    void equivalentAmountsUseTheSameTwoDigitScale() {
        assertThat(new TransferRequest(source, destination, new BigDecimal("1.5"), "CAD").canonicalAmount())
                .isEqualTo(new BigDecimal("1.50"));
    }

    /** Checks the domain rules that prevent identical endpoints and require CAD. */
    @Test
    void rejectsSelfTransferAndUnsupportedCurrency() {
        assertThatThrownBy(() -> new TransferRequest(source, source, BigDecimal.ONE, "CAD").canonicalAmount())
                .isInstanceOf(BankingException.class)
                .extracting(error -> ((BankingException) error).code()).isEqualTo("INVALID_ACCOUNTS");
        assertThatThrownBy(() -> new TransferRequest(source, destination, BigDecimal.ONE, "USD").canonicalAmount())
                .isInstanceOf(BankingException.class)
                .extracting(error -> ((BankingException) error).code()).isEqualTo("UNSUPPORTED_CURRENCY");
    }
}
