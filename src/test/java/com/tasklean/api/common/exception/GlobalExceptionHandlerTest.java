package com.tasklean.api.common.exception;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.common.ErrorCodes;
import com.tasklean.api.common.ErrorMessages;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static DataIntegrityViolationException valueTooLong() {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLException("value too long for type character varying(100)", "22001"));
    }

    @Test
    void handleNotFound_returns404WithMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNotFound(new ResourceNotFoundException("User not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::isSuccess, ApiResponse::getMessage)
                .containsExactly(false, "User not found");
    }

    @Test
    void handleDuplicate_returns409WithMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDuplicate(new DuplicateResourceException("Email already registered"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::getMessage)
                .isEqualTo("Email already registered");
    }

    @Test
    void handleBusinessRule_returns409WithMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusinessRule(new BusinessRuleException("A group must have at least one admin"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::getMessage)
                .isEqualTo("A group must have at least one admin");
    }

    @Test
    void handleAuthFailure_emailNotVerified_returns401WithCode() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAuthFailure(new EmailNotVerifiedException("Email not verified. Check your inbox for a verification code"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::isSuccess, ApiResponse::getMessage, ApiResponse::getCode)
                .containsExactly(false, "Email not verified. Check your inbox for a verification code", "EMAIL_NOT_VERIFIED");
    }

    @Test
    void handleAuthFailure_codedFailure_returns401WithThatCode() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAuthFailure(
                new AuthFailureException("That code is not valid.", ErrorCodes.TWO_FACTOR_CODE_INVALID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::isSuccess, ApiResponse::getMessage, ApiResponse::getCode)
                .containsExactly(false, "That code is not valid.", "TWO_FACTOR_CODE_INVALID");
    }

    @Test
    void handleBadCredentials_returns401WithMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBadCredentials(new BadCredentialsException("Invalid email or password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::getMessage)
                .isEqualTo("Invalid email or password");
    }

    @Test
    void handleAccessDenied_returns403WithGenericMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::getMessage)
                .isEqualTo(ErrorMessages.ACCESS_DENIED);
    }

    @Test
    void handleUnreadableBody_returns400() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnreadableBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::getMessage)
                .isEqualTo(ErrorMessages.MALFORMED_REQUEST);
    }

    @Test
    void handleTypeMismatch_returns400() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::getMessage)
                .isEqualTo(ErrorMessages.MALFORMED_REQUEST);
    }

    @Test
    void handleMethodNotAllowed_returns405() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotAllowed();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::getMessage)
                .isEqualTo(ErrorMessages.METHOD_NOT_ALLOWED);
    }

    @Test
    void handleNoResource_returns404WithEndpointMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleNoResource();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::getMessage)
                .isEqualTo(ErrorMessages.ENDPOINT_NOT_FOUND);
    }

    @Test
    void handleValidation_joinsFieldErrors() {
        MethodParameter parameter = mock(MethodParameter.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("obj", "email", "must not be blank"),
                new FieldError("obj", "password", "size must be at least 8")));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .contains("email: must not be blank")
                .contains("password: size must be at least 8");
    }

    @Test
    void handleDataIntegrityViolation_returns400WithGenericMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDataIntegrityViolation(valueTooLong());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::isSuccess, ApiResponse::getMessage)
                .containsExactly(false, ErrorMessages.MALFORMED_REQUEST);
    }

    @Test
    void handleDataIntegrityViolation_doesNotEchoTheDatabaseMessage() {
        DataIntegrityViolationException duplicate = new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("duplicate key value violates unique constraint \"user_email_key\" "
                        + "Detail: Key (email)=(ada@example.com) already exists.", "23505"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(duplicate);

        // The driver's message quotes the offending value, which on this table is an address.
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo(ErrorMessages.MALFORMED_REQUEST)
                .doesNotContain("ada@example.com", "user_email_key");
    }

    @Test
    void handleUnexpected_returns500WithGenericMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::getMessage)
                .isEqualTo(ErrorMessages.INTERNAL_ERROR);
    }

    @Test
    void handleEmailDelivery_returns503WithCode() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleEmailDelivery(
                new EmailDeliveryException(ErrorMessages.EMAIL_DELIVERY_FAILED, new RuntimeException()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::isSuccess, ApiResponse::getMessage, ApiResponse::getCode)
                .containsExactly(false, ErrorMessages.EMAIL_DELIVERY_FAILED,
                        ErrorCodes.EMAIL_DELIVERY_FAILED);
    }
}
