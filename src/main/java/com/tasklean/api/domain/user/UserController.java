package com.tasklean.api.domain.user;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.user.dto.UserRequest;
import com.tasklean.api.domain.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for user profiles — fetch, list, update, and account deletion.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Fetches a user by public UID.
     *
     * @param uid the user's public UID
     * @return {@code 200 OK} with the user
     */
    @GetMapping("/{uid}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable String uid) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserByUid(uid)));
    }

    /**
     * Lists all users.
     *
     * @return {@code 200 OK} with the users
     */
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
    @PutMapping("/{uid}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable String uid, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateUser(uid, request)));
    }

    /**
     * Soft-deletes a user account.
     *
     * @param uid the user's public UID
     * @return {@code 200 OK} with a confirmation message
     */
    @DeleteMapping("/{uid}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String uid) {
        userService.deleteUser(uid);
        return ResponseEntity.ok(ApiResponse.success("User deleted", null));
    }
}
