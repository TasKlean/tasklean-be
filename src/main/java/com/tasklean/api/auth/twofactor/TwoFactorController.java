package com.tasklean.api.auth.twofactor;

import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.auth.twofactor.dto.*;
import com.tasklean.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * The caller's own two-factor settings: see the current state, enroll an authenticator app,
 * confirm it, turn it off, and regenerate recovery codes. Every endpoint acts on the authenticated
 * caller, resolved from the JWT principal - never on an id from the request.
 */
@Tag(name = "Two-factor authentication", description = "The caller's own two-factor setup: enroll an authenticator app, turn it on or off, and manage recovery codes.")
@RestController
@RequestMapping("/api/account/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    /**
     * Returns the caller's two-factor state.
     *
     * @param principal the authenticated caller
     * @return {@code 200 OK} with whether two-factor is on or pending and how many recovery codes
     *         are left
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ApiResponse<TwoFactorStatusResponse>> getStatus(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(twoFactorService.getStatus(principal.userId())));
    }

    /**
     * Starts setup by enrolling a new, unconfirmed factor for the caller's authenticator app.
     *
     * @param principal the authenticated caller
     * @param request   the confirmation password (required only for a password account)
     * @return {@code 200 OK} with the secret and the {@code otpauth://} URI to show as a QR code
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<TwoFactorSetupResponse>> startSetup(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody TwoFactorSetupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(twoFactorService.startSetup(principal.userId(), request)));
    }

    /**
     * Confirms the pending setup with a code from the app, turning two-factor on and signing out
     * other devices.
     *
     * @param principal the authenticated caller
     * @param request   the code currently shown by the authenticator app
     * @return {@code 200 OK} with the one-time recovery codes and fresh tokens for this device
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<TwoFactorEnableResponse>> enable(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody TwoFactorEnableRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Two-factor authentication is on. Save your recovery codes - they are shown only once.",
                twoFactorService.enable(principal.userId(), request)));
    }

    /**
     * Turns two-factor off, removing the factor and every recovery code.
     *
     * @param principal the authenticated caller
     * @param request   the confirmation password (required only for a password account) and a
     *                  current authenticator or recovery code
     * @return {@code 200 OK} with fresh tokens for this device
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<AuthResponse>> disable(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody TwoFactorConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Two-factor authentication is off",
                twoFactorService.disable(principal.userId(), request)));
    }

    /**
     * Issues a new set of recovery codes, invalidating the previous set.
     *
     * @param principal the authenticated caller
     * @param request   the confirmation password (required only for a password account) and a
     *                  current authenticator or recovery code
     * @return {@code 200 OK} with the new one-time recovery codes
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/recovery-codes")
    public ResponseEntity<ApiResponse<RecoveryCodesResponse>> regenerateRecoveryCodes(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody TwoFactorConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "New recovery codes generated. Your previous codes no longer work.",
                twoFactorService.regenerateRecoveryCodes(principal.userId(), request)));
    }
}
