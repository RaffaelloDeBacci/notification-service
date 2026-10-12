package com.qualifyze.notification.adapter.rest.api;

import com.qualifyze.notification.adapter.rest.api.contract.model.ErrorReason;
import com.qualifyze.notification.adapter.rest.api.contract.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void shouldReturnBadRequestWhenRequestBodyValidationFails() throws NoSuchMethodException {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/notifications");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "fieldA", "must not be blank"));
        bindingResult.addError(new FieldError("request", "fieldB", " "));
        Method method = ApiExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new MethodParameter(method, 0),
                bindingResult
        );

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleInvalidBody(exception, request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("NOTIF-VAL-001", response.getBody().getCode());
        assertEquals(ErrorReason.VALIDATION_FAILED, response.getBody().getReason());
        assertEquals("Request validation failed", response.getBody().getMessage());
        assertEquals("/api/v1/notifications", response.getBody().getPath());
        assertEquals(2, response.getBody().getDetails().size());
        assertEquals("fieldA: must not be blank", response.getBody().getDetails().get(0));
        assertEquals("fieldB", response.getBody().getDetails().get(1));
    }

    @Test
    void shouldReturnBadRequestWhenRequestBodyIsUnreadable() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/notifications");
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "Malformed request",
                mock(HttpInputMessage.class)
        );

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(exception, request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("NOTIF-VAL-002", response.getBody().getCode());
        assertEquals(ErrorReason.VALIDATION_FAILED, response.getBody().getReason());
        assertEquals("Malformed request payload", response.getBody().getMessage());
        assertEquals("/api/v1/notifications", response.getBody().getPath());
        assertEquals(0, response.getBody().getDetails().size());
    }

    @Test
    void shouldReturnBadRequestWhenConstraintViolationsOccur() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/customers/customer-1/distribution-list");
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("customerReference");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be blank");
        ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation));

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(exception, request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("NOTIF-VAL-001", response.getBody().getCode());
        assertEquals(ErrorReason.VALIDATION_FAILED, response.getBody().getReason());
        assertEquals("Request validation failed", response.getBody().getMessage());
        assertEquals("customerReference: must not be blank", response.getBody().getDetails().get(0));
    }

    @Test
    void shouldReturnServiceUnavailableWithoutInternalsWhenDatabaseIsUnreachable() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/notifications");
        DataAccessResourceFailureException exception =
                new DataAccessResourceFailureException("Connection to db-host:5432 refused");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleDependencyUnavailable(exception, request);

        // Assert
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("NOTIF-DEP-001", response.getBody().getCode());
        assertEquals(ErrorReason.DEPENDENCY_UNAVAILABLE, response.getBody().getReason());
        assertEquals("A required dependency is temporarily unavailable", response.getBody().getMessage());
        assertEquals(0, response.getBody().getDetails().size());
    }

    @Test
    void shouldReturnServiceUnavailableWhenDatabaseFailureIsTransient() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/notifications");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleDependencyUnavailable(
                new QueryTimeoutException("statement timeout"), request);

        // Assert
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void shouldMapIllegalStateExceptionToInternalErrorWithoutEchoingMessage() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/notifications");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
                new IllegalStateException("Inbox event reference not found: secret-ref"), request);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("NOTIF-INT-001", response.getBody().getCode());
        assertEquals("Unexpected internal error", response.getBody().getMessage());
        assertEquals(0, response.getBody().getDetails().size());
    }

    @Test
    void shouldReturnUnprocessableEntityWhenBusinessRuleValidationFails() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/customers/c1/distribution-list");
        IllegalArgumentException exception = new IllegalArgumentException("targets is required");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleBusinessRuleViolation(exception, request);

        // Assert
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("NOTIF-BIZ-001", response.getBody().getCode());
        assertEquals(ErrorReason.BUSINESS_RULE_VIOLATION, response.getBody().getReason());
        assertEquals("Business rule validation failed", response.getBody().getMessage());
        assertEquals("targets is required", response.getBody().getDetails().get(0));
    }

    @Test
    void shouldReturnNotFoundWhenEndpointDoesNotExist() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/missing");
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "/api/v1/missing", "missing");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(exception, request);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("NOTIF-RES-404", response.getBody().getCode());
        assertEquals(ErrorReason.NOT_FOUND, response.getBody().getReason());
        assertEquals("Endpoint not found", response.getBody().getMessage());
        assertEquals(0, response.getBody().getDetails().size());
    }

    @Test
    void shouldReturnNotFoundWhenHandlerDoesNotExist() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/missing");
        NoHandlerFoundException exception = new NoHandlerFoundException("GET", "/api/v1/missing", HttpHeaders.EMPTY);

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(exception, request);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("NOTIF-RES-404", response.getBody().getCode());
        assertEquals(ErrorReason.NOT_FOUND, response.getBody().getReason());
    }

    @Test
    void shouldReturnInternalServerErrorWhenUnexpectedExceptionOccurs() {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/notifications");
        RuntimeException exception = new RuntimeException("unexpected");

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(exception, request);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("NOTIF-INT-001", response.getBody().getCode());
        assertEquals(ErrorReason.INTERNAL_ERROR, response.getBody().getReason());
        assertEquals("Unexpected internal error", response.getBody().getMessage());
        assertEquals(0, response.getBody().getDetails().size());
    }

    private void dummyMethod(String value) {
    }
}
