/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: TransferController.java
 * Description: Transfer entry point and requester-scoped lookup for uncertain network outcomes.
 * Project version: 0.1.0
 */

package com.northstar.banking.transfers;

import java.util.UUID;
import com.northstar.banking.authentication.CustomerIdentity;
import com.northstar.banking.common.RequestIdFilter;
import com.northstar.banking.ledger.PostingReceipt;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Transfer entry point and requester-scoped lookup for uncertain network outcomes. */
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {
    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    /** Accepts the retry key and distinguishes a new posting from a replay in the HTTP status. */
    @PostMapping
    @ApiResponse(responseCode = "201", description = "New transfer committed", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "200", description = "Original receipt replayed", useReturnTypeSchema = true)
    ResponseEntity<PostingReceipt> transfer(@AuthenticationPrincipal CustomerIdentity customer,
                                            @RequestHeader("Idempotency-Key") UUID key,
                                            @Valid @RequestBody TransferRequest body, HttpServletRequest request) {
        var outcome = transfers.transfer(customer.id(), key, body, RequestIdFilter.id(request));
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(outcome.receipt());
    }

    /** Looks up a requester-owned receipt by transfer ID. */
    @GetMapping("/{transferId}")
    PostingReceipt find(@AuthenticationPrincipal CustomerIdentity customer, @PathVariable UUID transferId) {
        return transfers.find(customer.id(), transferId);
    }

    /** Lets the requester check a possibly committed transfer by its original key. */
    @GetMapping("/by-key/{key}")
    PostingReceipt findByKey(@AuthenticationPrincipal CustomerIdentity customer, @PathVariable UUID key) {
        return transfers.findByKey(customer.id(), key);
    }
}
