package com.qualifyze.notification.adapter.rest.api;

import com.qualifyze.notification.adapter.rest.api.contract.model.ErrorReason;
import com.qualifyze.notification.adapter.rest.api.contract.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<String> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(ApiExceptionHandler::toFieldMessage)
                .toList();
        return build(
                HttpStatus.BAD_REQUEST,
                "NOTIF-VAL-001",
                ErrorReason.VALIDATION_FAILED,
                "Request validation failed",
                details,
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "NOTIF-VAL-002",
                ErrorReason.VALIDATION_FAILED,
                "Malformed request payload",
                List.of(),
                request
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        List<String> details = exception.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();
        return build(
                HttpStatus.BAD_REQUEST,
                "NOTIF-VAL-001",
                ErrorReason.VALIDATION_FAILED,
                "Request validation failed",
                details,
                request
        );
    }

    /**
     * Only genuine infrastructure outages map to 503, so its retry hint is truthful.
     * The exception message is logged, never returned: it can expose internals.
     */
    @ExceptionHandler({TransientDataAccessException.class, DataAccessResourceFailureException.class})
    ResponseEntity<ErrorResponse> handleDependencyUnavailable(DataAccessException exception, HttpServletRequest request) {
        LOGGER.error("Dependency unavailable on {} {}", request.getMethod(), request.getRequestURI(), exception);
        return build(
                HttpStatus.SERVICE_UNAVAILABLE,
                "NOTIF-DEP-001",
                ErrorReason.DEPENDENCY_UNAVAILABLE,
                "A required dependency is temporarily unavailable",
                List.of(),
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> handleBusinessRuleViolation(IllegalArgumentException exception, HttpServletRequest request) {
        return build(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "NOTIF-BIZ-001",
                ErrorReason.BUSINESS_RULE_VIOLATION,
                "Business rule validation failed",
                List.of(exception.getMessage()),
                request
        );
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ErrorResponse> handleNotFound(Exception exception, HttpServletRequest request) {
        return build(
                HttpStatus.NOT_FOUND,
                "NOTIF-RES-404",
                ErrorReason.NOT_FOUND,
                "Endpoint not found",
                List.of(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), exception);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "NOTIF-INT-001",
                ErrorReason.INTERNAL_ERROR,
                "Unexpected internal error",
                List.of(),
                request
        );
    }

    private static ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String code,
            ErrorReason reason,
            String message,
            List<String> details,
            HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse()
                .code(code)
                .reason(reason)
                .message(message)
                .timestamp(OffsetDateTime.now())
                .path(request.getRequestURI())
                .details(details);
        return ResponseEntity.status(status).body(error);
    }

    private static String toFieldMessage(FieldError fieldError) {
        if (fieldError.getDefaultMessage() == null || fieldError.getDefaultMessage().isBlank()) {
            return fieldError.getField();
        }
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
