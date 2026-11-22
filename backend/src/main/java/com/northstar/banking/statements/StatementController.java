/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: StatementController.java
 * Description: Delivers an owned-account preview or attachment without buffering its CSV in the controller.
 * Project version: 0.1.0
 */

package com.northstar.banking.statements;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import com.northstar.banking.audit.AuditService;
import com.northstar.banking.authentication.CustomerIdentity;
import com.northstar.banking.common.RequestIdFilter;
import com.northstar.banking.ledger.LedgerWindow;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Delivers an owned-account preview or attachment without buffering its CSV in the controller. */
@RestController
@RequestMapping("/api/v1/statements")
public class StatementController {
    private final StatementService statements;
    private final AuditService audit;

    public StatementController(StatementService statements, AuditService audit) {
        this.statements = statements;
        this.audit = audit;
    }

    /** Returns review totals for an owned account and date range. */
    @GetMapping
    StatementService.StatementSummary preview(@AuthenticationPrincipal CustomerIdentity customer,
                                               @RequestParam UUID accountId,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statements.preview(customer.id(), accountId, LedgerWindow.statement(from, to));
    }

    /** Validates the export selection before constructing a streamed attachment. */
    @GetMapping("/download")
    ResponseEntity<StreamingResponseBody> download(@AuthenticationPrincipal CustomerIdentity customer,
                                                   @RequestParam UUID accountId,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(defaultValue = "CSV") StatementService.StatementFormat format,
                                                   HttpServletRequest request) {
        var window = LedgerWindow.statement(from, to);
        var summary = statements.preview(customer.id(), accountId, window);
        boolean pdf = format == StatementService.StatementFormat.PDF;
        if (pdf) {
            statements.checkPdfSize(summary);
        }
        UUID requestId = RequestIdFilter.id(request);
        StreamingResponseBody body = output -> {
            statements.write(customer.id(), accountId, window, format, output);
            // The read-only snapshot is already closed when the audit event is written.
            audit.success(customer.id(), "STATEMENT_GENERATED", "ACCOUNT", accountId, requestId);
        };
        String filename = "northstar-statement-" + from + "-" + to + "." + format.name().toLowerCase(Locale.ROOT);
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}
