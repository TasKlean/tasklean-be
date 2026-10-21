package com.tasklean.api.auth.account;

import com.tasklean.api.auth.account.dto.ChangePasswordRequest;
import com.tasklean.api.auth.account.dto.ConnectedAccountsResponse;
import com.tasklean.api.auth.account.dto.DeactivateAccountRequest;
import com.tasklean.api.auth.account.dto.SetPasswordRequest;
import com.tasklean.api.auth.dto.AuthResponse;
import com.tasklean.api.auth.dto.GoogleAuthRequest;
import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * The caller's own account-security settings: change or set a password, view and manage connected
 * sign-in methods (Google link/unlink), and deactivate the account. Every endpoint acts on the
 * authenticated caller, resolved from the JWT principal — never on an id from the request.
 */
@Tag(name = "Account", description = "The caller's own account security: change/set password, connected accounts (Google), and deactivation.")
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * Changes the caller's password, signing out other devices and keeping this one.
     *
     * @param principal the authenticated caller
     * @param request   the current and new passwords
     * @return {@code 200 OK} with a fresh auth response (new tokens) for the current device
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.success(accountService.changePassword(principal.userId(), request)));
    }

    /**
     * Sets a password on a Google-only account, signing out other devices and keeping this one.
     *
     * @param principal the authenticated caller
     * @param request   the new password
     * @return {@code 200 OK} with a fresh auth response (new tokens) for the current device
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/set-password")
    public ResponseEntity<ApiResponse<AuthResponse>> setPassword(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody SetPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.success(accountService.setPassword(principal.userId(), request)));
    }

    /**
     * Returns the caller's active sign-in methods (password set, Google linked).
     *
     * @param principal the authenticated caller
     * @return {@code 200 OK} with the connected-accounts summary
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/connected-accounts")
    public ResponseEntity<ApiResponse<ConnectedAccountsResponse>> getConnections(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getConnections(principal.userId())));
    }

    /**
     * Links a Google account to the caller after verifying the Google ID token.
     *
     * @param principal the authenticated caller
     * @param request   the Google ID token and nonce secret
     * @return {@code 200 OK} with the updated connected-accounts summary
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/connected-accounts/google")
    public ResponseEntity<ApiResponse<ConnectedAccountsResponse>> linkGoogle(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(ApiResponse.success(accountService.linkGoogle(principal.userId(), request)));
    }

    /**
     * Unlinks the caller's Google account (refused if it would leave no way to sign in).
     *
     * @param principal the authenticated caller
     * @return {@code 200 OK} with the updated connected accounts summary
     */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/connected-accounts/google")
    public ResponseEntity<ApiResponse<ConnectedAccountsResponse>> unlinkGoogle(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(accountService.unlinkGoogle(principal.userId())));
    }

    /**
     * Deactivates the caller's account and signs out every session.
     *
     * @param principal the authenticated caller
     * @param request   the confirmation password (required only for a password account)
     * @return {@code 200 OK} with a confirmation message
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody DeactivateAccountRequest request) {
        accountService.deactivate(principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.success("Your account has been deactivated", null));
    }
}
