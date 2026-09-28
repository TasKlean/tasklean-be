package com.tasklean.api.domain.user;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.user.dto.UserRequest;
import com.tasklean.api.domain.user.dto.UserResponse;
import com.tasklean.api.domain.user.dto.UserRoleRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for user profiles — fetch (own via {@code /me}, or by UID), list, update,
 * account deletion, and platform-role management. A user may act on their own account; platform
 * admins/super-admins have wider access.
 */
@Tag(name = "Users", description = "User profiles: the caller's own via /me, plus platform-admin listing and role management.")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Fetches the authenticated caller's own profile. Takes no identifier — the user is resolved
     * from the JWT principal, so a client never has to track or send its own UID.
     *
     * @param principal the authenticated caller
     * @return {@code 200 OK} with the caller's profile
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserByUid(principal.uid())));
    }

    /**
     * Fetches a user by public UID.
     *
     * @param uid the user's public UID
     * @return {@code 200 OK} with the user
     */
    @PreAuthorize("hasRole('ADMIN') or @accountSecurity.isSelf(#uid)")
    @GetMapping("/{uid}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable String uid) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserByUid(uid)));
    }

    /**
     * Lists all users.
     *
     * @return {@code 200 OK} with the users
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers()));
    }

    /**
     * Updates a user's profile.
     *
     * @param uid     the user's public UID
     * @param request the new profile details
     * @return {@code 200 OK} with the updated user
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or @accountSecurity.isSelf(#uid)")
    @PutMapping("/{uid}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable String uid, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateUser(uid, request)));
    }

    /**
     * Changes a user's platform role.
     *
     * @param uid     the user's public UID
     * @param request the new platform role
     * @return {@code 200 OK} with the updated user
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{uid}/role")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRole(
            @PathVariable String uid, @Valid @RequestBody UserRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateUserRole(uid, request.getRole())));
    }

    /**
     * Soft-deletes a user account.
     *
     * @param uid the user's public UID
     * @return {@code 200 OK} with a confirmation message
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or @accountSecurity.isSelf(#uid)")
    @DeleteMapping("/{uid}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String uid) {
        userService.deleteUser(uid);
        return ResponseEntity.ok(ApiResponse.success("User deleted", null));
    }
}
