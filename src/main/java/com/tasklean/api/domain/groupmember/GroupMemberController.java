package com.tasklean.api.domain.groupmember;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.groupmember.dto.GroupMemberRequest;
import com.tasklean.api.domain.groupmember.dto.GroupMemberResponse;
import com.tasklean.api.domain.groupmember.dto.GroupMemberRoleRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for a single group membership — add, fetch, change role, and remove or leave.
 * A membership is addressed by its own UID; the group's own listing of its members lives on
 * {@code GET /api/groups/{uid}/members}.
 */
@Tag(name = "Group members", description = "Membership of a household: add, fetch, change role, remove or leave.")
@RestController
@RequestMapping("/api/group-members")
@RequiredArgsConstructor
public class GroupMemberController {

    private final GroupMemberService groupMemberService;

    /**
     * Adds a user to a group directly. This is platform tooling: a household admin invites people
     * by sharing the group's invite code, which is the only route that asks for the joiner's consent.
     *
     * @param request the group, the user to add, and the role to give them
     * @return {@code 201 Created} with the created membership
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<GroupMemberResponse>> addMember(
            @Valid @RequestBody GroupMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(groupMemberService.addMember(request)));
    }

    /**
     * Fetches a membership by its UID.
     *
     * @param memberUid the membership's public UID
     * @return {@code 200 OK} with the membership
     */
    @PreAuthorize("hasRole('ADMIN') or @groupSecurity.canViewMember(#memberUid)")
    @GetMapping("/{memberUid}")
    public ResponseEntity<ApiResponse<GroupMemberResponse>> getMember(@PathVariable String memberUid) {
        return ResponseEntity.ok(ApiResponse.success(groupMemberService.getMember(memberUid)));
    }

    /**
     * Changes a member's role within their group.
     *
     * @param memberUid the membership's public UID
     * @param request   the new role
     * @return {@code 200 OK} with the updated membership
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or @groupSecurity.canManageMember(#memberUid)")
    @PatchMapping("/{memberUid}/role")
    public ResponseEntity<ApiResponse<GroupMemberResponse>> updateRole(
            @PathVariable String memberUid, @Valid @RequestBody GroupMemberRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                groupMemberService.updateMemberRole(memberUid, request.getRole())));
    }

    /**
     * Removes a membership (soft). A member removing their own membership is leaving the group,
     * which the service records as a distinct event.
     *
     * @param memberUid the membership's public UID
     * @param principal the authenticated caller
     * @return {@code 200 OK} with a confirmation message
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or @groupSecurity.canManageMember(#memberUid)"
            + " or @groupSecurity.isSelfMember(#memberUid)")
    @DeleteMapping("/{memberUid}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable String memberUid, @AuthenticationPrincipal AuthPrincipal principal) {
        boolean left = groupMemberService.removeMember(memberUid, principal.userId());
        return ResponseEntity.ok(ApiResponse.success(left ? "Left group" : "Member removed", null));
    }
}
