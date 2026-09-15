package com.tasklean.api.common.exception;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.common.ErrorMessages;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

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
    void handleUnexpected_returns500WithGenericMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ApiResponse::getMessage)
                .isEqualTo(ErrorMessages.INTERNAL_ERROR);
    }
}
