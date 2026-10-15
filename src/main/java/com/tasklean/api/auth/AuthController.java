package com.tasklean.api.auth;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.dto.GoogleAuthRequest;
import com.tasklean.api.auth.dto.LoginRequest;
import com.tasklean.api.auth.dto.RegisterRequest;
import com.tasklean.api.auth.passwordreset.PasswordResetService;
import com.tasklean.api.auth.passwordreset.dto.ForgotPasswordRequest;
import com.tasklean.api.auth.passwordreset.dto.ResetPasswordRequest;
import com.tasklean.api.auth.refresh.dto.RefreshRequest;
import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.auth.verification.dto.ResendVerificationRequest;
import com.tasklean.api.auth.verification.VerificationService;
import com.tasklean.api.auth.verification.dto.VerifyEmailRequest;
import com.tasklean.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Auth", description = "Registration, login, email verification, Google sign-in, token refresh and logout. Public - these endpoints need no token.")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@SecurityRequirements
public class AuthController {

    private final AuthService authService;
    private final GoogleOAuthService googleOAuthService;
    private final VerificationService verificationService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;

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
     * Authenticates with a Google ID token, provisioning or linking the account as needed.
     *
     * @param request the request carrying the Google ID token
     * @return {@code 200 OK} with the auth response (access + refresh tokens)
     */
    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> google(@Valid @RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(ApiResponse.success(googleOAuthService.authenticate(request)));
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
     * Starts a password reset by emailing a single-use reset link.
     *
     * @param request the account email
     * @return {@code 200 OK} with a neutral message (does not reveal whether the email exists)
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request);
        return ResponseEntity.ok(ApiResponse.success("If that email is registered, a reset link has been sent", null));
    }

    /**
     * Completes a password reset with a valid token and sets the new password.
     *
     * @param request the reset token and new password
     * @return {@code 200 OK} on success; the user must then sign in with the new password
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset. You can now sign in with your new password.", null));
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
     * Logs out the current device by revoking the session the presented refresh token belongs to.
     *
     * @param request the refresh token identifying the device/session
     * @return {@code 200 OK} with a confirmation message
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokenService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    /**
     * Logs out everywhere by revoking every refresh token for the user the presented token
     * identifies.
     *
     * @param request the refresh token identifying the user
     * @return {@code 200 OK} with a confirmation message
     */
    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(@Valid @RequestBody RefreshRequest request) {
        refreshTokenService.logoutAll(request);
        return ResponseEntity.ok(ApiResponse.success("Logged out of all sessions", null));
    }
}
