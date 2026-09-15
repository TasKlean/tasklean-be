package com.tasklean.api.domain.group;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.group.dto.GroupRequest;
import com.tasklean.api.domain.group.dto.GroupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for groups (households) — create, fetch, list, update, and delete.
 */
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    /**
     * Creates a group. Any authenticated user; the creator becomes the group's first admin.
     *
     * @param request   the group details (name, description, photo)
     * @param principal the authenticated caller
     * @return {@code 201 Created} with the created group
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
            @Valid @RequestBody GroupRequest request, @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(groupService.createGroup(request, principal.userId())));
    }

    /**
     * Fetches a group by public UID.
     *
     * @param uid the group's public UID
     * @return {@code 200 OK} with the group
     */
    @PreAuthorize("hasRole('ADMIN') or @groupSecurity.isMember(#uid)")
    @GetMapping("/{uid}")
    public ResponseEntity<ApiResponse<GroupResponse>> getGroup(@PathVariable String uid) {
        return ResponseEntity.ok(ApiResponse.success(groupService.getGroupByUid(uid)));
    }

    /**
     * Lists all groups.
     *
     * @return {@code 200 OK} with the groups
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<GroupResponse>>> getAllGroups() {
        return ResponseEntity.ok(ApiResponse.success(groupService.getAllGroups()));
    }

    /**
     * Updates a group.
     *
     * @param uid     the group's public UID
     * @param request the new group details
     * @return {@code 200 OK} with the updated group
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or @groupSecurity.isAdmin(#uid)")
    @PutMapping("/{uid}")
    public ResponseEntity<ApiResponse<GroupResponse>> updateGroup(
            @PathVariable String uid, @Valid @RequestBody GroupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(groupService.updateGroup(uid, request)));
    }

    /**
     * Soft-deletes a group.
     *
     * @param uid the group's public UID
     * @return {@code 200 OK} with a confirmation message
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or @groupSecurity.isAdmin(#uid)")
    @DeleteMapping("/{uid}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable String uid) {
        groupService.deleteGroup(uid);
        return ResponseEntity.ok(ApiResponse.success("Group deleted", null));
    }
}
