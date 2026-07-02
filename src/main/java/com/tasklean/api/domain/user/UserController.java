package com.tasklean.api.domain.user;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.user.dto.UserRequest;
import com.tasklean.api.domain.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{uid}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable String uid) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserByUid(uid)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers()));
    }

    @PutMapping("/{uid}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable String uid, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateUser(uid, request)));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String uid) {
        userService.deleteUser(uid);
        return ResponseEntity.ok(ApiResponse.success("User deleted", null));
    }
}
