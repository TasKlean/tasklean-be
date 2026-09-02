package com.tasklean.api.auth;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.dto.LoginRequest;
import com.tasklean.api.auth.dto.RegisterRequest;
import com.tasklean.api.auth.refresh.dto.RefreshRequest;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.auth.verification.dto.ResendVerificationRequest;
import com.tasklean.api.auth.verification.VerificationService;
import com.tasklean.api.auth.verification.dto.VerifyEmailRequest;
import com.tasklean.api.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints — registration, login, email verification,
 * token refresh, and logout. Not protected by the JWT filter.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final VerificationService verificationService;
    private final RefreshTokenService refreshTokenService;

    /**
     * Registers a new user and sends a verification code. No JWT is issued until verified.
     *
     * @param request the registration details (email, password, name)
     * @return {@code 201 Created} with the pending-verification auth response
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.register(request)));
    }

    /**
     * Authenticates with email and password.
     *
     * @param request the login credentials
     * @return {@code 200 OK} with the auth response (access + refresh tokens)
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    /**
     * Validates an email verification code and completes verification.
     *
     * @param request the email and verification code
     * @return {@code 200 OK} with the auth response on success
     */
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(ApiResponse.success(verificationService.verifyEmail(request)));
    }

    /**
     * Resends a fresh verification code to an unverified user.
     *
     * @param request the email to resend the code to
     * @return {@code 200 OK} with a neutral message (does not reveal whether the email exists)
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        verificationService.resendVerification(request);
        return ResponseEntity.ok(ApiResponse.success("If this email is registered, a verification code has been sent", null));
    }

    /**
     * Issues a new access + refresh token pair using a valid refresh token (rotation).
     *
     * @param request the current refresh token
     * @return {@code 200 OK} with the refreshed auth response
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.success(refreshTokenService.refresh(request)));
    }

    /**
     * Revokes all refresh tokens for the user (logout).
     *
     * @param request the refresh token identifying the user
     * @return {@code 200 OK} with a confirmation message
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokenService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }
}
