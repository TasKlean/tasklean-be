package com.tasklean.api.domain.usersettings;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.usersettings.dto.UserSettingsRequest;
import com.tasklean.api.domain.usersettings.dto.UserSettingsResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * The caller's own UI settings (colour theme + language). Both endpoints resolve the user from the
 * JWT principal and take no identifier, so a user only ever reads or writes their own settings.
 */
@Tag(name = "User settings", description = "The caller's own UI settings: colour theme and language.")
@RestController
@RequestMapping("/api/user-settings")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    /**
     * Fetches the caller's settings, falling back to the defaults when none are saved yet.
     *
     * @param principal the authenticated caller
     * @return {@code 200 OK} with the caller's settings
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ApiResponse<UserSettingsResponse>> getMySettings(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(userSettingsService.getMySettings(principal.userId())));
    }

    /**
     * Replaces the caller's settings, creating them on the first write.
     *
     * @param principal the authenticated caller
     * @param request   the new theme and locale
     * @return {@code 200 OK} with the saved settings
     */
    @PreAuthorize("isAuthenticated()")
    @PutMapping
    public ResponseEntity<ApiResponse<UserSettingsResponse>> updateMySettings(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UserSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userSettingsService.updateMySettings(principal.userId(), request)));
    }
}
