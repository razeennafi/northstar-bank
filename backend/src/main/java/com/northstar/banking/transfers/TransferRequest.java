/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: TransferRequest.java
 * Description: Validated HTTP input and canonical decimal rules shared with direct service callers.
 * Project version: 0.1.0
 */

package com.northstar.banking.transfers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import com.northstar.banking.common.BankingException;
import com.northstar.banking.ledger.LedgerService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

/** Validated HTTP input and canonical decimal rules shared with direct service callers. */
public record TransferRequest(@NotNull UUID sourceAccountId, @NotNull UUID destinationAccountId,
                              @NotNull @Schema(type = "string", example = "125.50") BigDecimal amount,
                              @NotBlank @Size(max = 3) @Schema(example = "CAD") String currency) {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999999.99");

    /**
     * Validates a CAD transfer and normalizes its amount to two decimals.
     * Additional precision is rejected before PostgreSQL could round it.
     *
     * @return canonical amount for spending and idempotency comparison
     * @throws BankingException for unsupported currency, invalid amount or self-transfer
     */
    public BigDecimal canonicalAmount() {
        if (sourceAccountId == null || destinationAccountId == null || sourceAccountId.equals(destinationAccountId)) {
            throw invalid("INVALID_ACCOUNTS", "Choose two different accounts.");
        }
        if (!LedgerService.CURRENCY.equals(currency)) {
            throw invalid("UNSUPPORTED_CURRENCY", "Transfers support CAD only.");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw invalid("INVALID_AMOUNT", "Enter a positive CAD amount with up to two decimal places.");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private BankingException invalid(String code, String message) {
        return new BankingException(HttpStatus.BAD_REQUEST, code, message);
    }
}
