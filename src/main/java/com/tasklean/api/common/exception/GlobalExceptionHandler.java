package com.tasklean.api.common.exception;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.common.ErrorMessages;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Central exception handling for all controllers. Maps exceptions to the {@link ApiResponse}
 * envelope with the correct HTTP status. Only failed authentication (401, WARN) and the
 * catch-all (500, ERROR + stack trace) log explicitly; 4xx handlers rely on the
 * request-summary line from {@code RequestLoggingFilter}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Maps a missing resource to 404.
     *
     * @param ex the thrown exception
     * @return a 404 response with the exception message
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Maps a duplicate/conflicting resource to 409.
     *
     * @param ex the thrown exception
     * @return a 409 response with the exception message
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateResourceException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Maps a failed authentication to 401.
     *
     * @param ex the thrown exception
     * @return a 401 response with the exception message
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        // Security-relevant (failed auth) — WARN so it's visible for brute-force monitoring. No stack trace needed.
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Maps an unknown route (no matching controller or static resource) to 404 in our envelope.
     *
     * @param ex the thrown exception
     * @return a 404 response with the endpoint-not-found message
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorMessages.ENDPOINT_NOT_FOUND));
    }

    /**
     * Maps bean-validation failures on request bodies to 400, joining field errors into one message.
     *
     * @param ex the validation exception
     * @return a 400 response listing the field errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

    /**
     * Catch-all for anything unhandled above: the only path that logs a full stack trace and the only
     * one that returns a generic message — internal details (exception type, SQL, etc.) are never leaked
     * to the client. The MDC context (requestId/userId) on the log line ties it back to the exact request.
     *
     * @param ex the unhandled exception
     * @return a 500 response with a generic error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorMessages.INTERNAL_ERROR));
    }
}
