/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: DemoFundingController.java
 * Description: Accepts an owned account ID.
 * Project version: 0.1.0
 */

package com.northstar.banking.accounts;

import java.util.UUID;
import com.northstar.banking.authentication.CustomerIdentity;
import com.northstar.banking.common.RequestIdFilter;
import com.northstar.banking.ledger.PostingReceipt;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Accepts an owned account ID. The grant amount and funding source are server-owned. */
@RestController
public class DemoFundingController {
    private final DemoFundingService funding;

    public DemoFundingController(DemoFundingService funding) {
        this.funding = funding;
    }

    /** Passes the authenticated customer and target account to the demo grant workflow. */
    @PostMapping("/api/v1/demo/funding")
    @ResponseStatus(HttpStatus.CREATED)
    PostingReceipt fund(@AuthenticationPrincipal CustomerIdentity customer, @Valid @RequestBody FundAccount body,
                        HttpServletRequest request) {
        return funding.fund(customer.id(), body.accountId(), RequestIdFilter.id(request));
    }

    record FundAccount(@NotNull UUID accountId) {
    }
}
