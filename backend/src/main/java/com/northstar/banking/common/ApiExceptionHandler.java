/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: ApiExceptionHandler.java
 * Description: Maps expected failures to a stable contract and keeps internal details out of HTTP.
 * Project version: 0.1.0
 */

package com.northstar.banking.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import com.northstar.banking.audit.AuditService;
import com.northstar.banking.authentication.CustomerIdentity;
import org.springframework.core.task.TaskRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

/** Maps expected failures to a stable contract and keeps internal details out of HTTP. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final AuditService audit;

    public ApiExceptionHandler(AuditService audit) {
        this.audit = audit;
    }

    /** Translates domain errors to their declared HTTP statuses and adds audit or retry details when applicable. */
    @ExceptionHandler(BankingException.class)
    ResponseEntity<ApiError> business(BankingException exception, HttpServletRequest request) {
        if ("ACCOUNT_UNAVAILABLE".equals(exception.code())
                && !request.getRequestURI().equals(request.getContextPath() + "/api/v1/transfers")) {
            auditDeniedAccount(request);
        }
        if (exception.status() == HttpStatus.TOO_MANY_REQUESTS) {
            return ResponseEntity.status(exception.status()).header(HttpHeaders.RETRY_AFTER, "60")
                    .body(ApiError.of(exception.code(), exception.getMessage(), request));
        }
        return response(exception.status(), exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class})
    /** Returns a safe client error for malformed or invalid arguments. */
    ResponseEntity<ApiError> invalid(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Check the request fields and try again.", request);
    }

    /** Returns the shared response for an unmatched route. */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> missing(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "This resource was not found.", request);
    }

    /** Reports that the route does not support the attempted HTTP method. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "This method is unavailable.", request);
    }

    /** Reports an unsupported request content type. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> mediaType(HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Send JSON for this operation.", request);
    }

    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class,
            TransientDataAccessException.class, TaskRejectedException.class, AsyncRequestTimeoutException.class})
    /** Maps transient infrastructure failures to a retryable service response. */
    ResponseEntity<ApiError> unavailable(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "TEMPORARILY_UNAVAILABLE",
                "The service is temporarily unavailable.", request);
    }

    /** Logs the internal failure and sends a generic response with a correlation ID. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        // Exception messages can embed SQL values or the original request.
        log.error("Unhandled request failure {} ({})", RequestIdFilter.id(request), exception.getClass().getName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_FAILURE", "An unexpected error occurred.", request);
    }

    /** Packages a status and safe error details into the common response contract. */
    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message,
                                              HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiError.of(code, message, request));
    }

    /** Records denied account access for the authenticated actor when context is available. */
    private void auditDeniedAccount(HttpServletRequest request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomerIdentity actor) {
            try {
                audit.rejected(actor.id(), "ACCOUNT_ACCESS", "ACCOUNT", null, RequestIdFilter.id(request));
            } catch (DataAccessException | TransactionException exception) {
                log.warn("Account-access audit unavailable for request {}", RequestIdFilter.id(request));
            }
        }
    }
}
