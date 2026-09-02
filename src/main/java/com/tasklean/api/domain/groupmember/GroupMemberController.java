package com.tasklean.api.domain.groupmember;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.groupmember.dto.GroupMemberRequest;
import com.tasklean.api.domain.groupmember.dto.GroupMemberResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for group membership — add member, fetch, list by group, change role, and remove.
 */
@RestController
@RequestMapping("/api/group-members")
@RequiredArgsConstructor
public class GroupMemberController {

    private final GroupMemberService groupMemberService;

    /**
     * Adds a user to a group.
     *
     * @param request the membership details (user, group, role)
     * @return {@code 201 Created} with the created membership
     */
    @PostMapping
    public ResponseEntity<ApiResponse<GroupMemberResponse>> addMember(@Valid @RequestBody GroupMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(groupMemberService.addMember(request)));
    }

    /**
     * Fetches a membership by id.
     *
     * @param id the membership id
     * @return {@code 200 OK} with the membership
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupMemberResponse>> getMember(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(groupMemberService.getMemberById(id)));
    }

    /**
     * Lists a group's active members.
     *
     * @param groupId the group id
     * @return {@code 200 OK} with the members
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<GroupMemberResponse>>> getMembersByGroup(@RequestParam Long groupId) {
        return ResponseEntity.ok(ApiResponse.success(groupMemberService.getMembersByGroup(groupId)));
    }

    /**
     * Changes a member's role.
     *
     * @param id   the membership id
     * @param role the new role
     * @return {@code 200 OK} with the updated membership
     */
    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<GroupMemberResponse>> updateRole(
            @PathVariable Long id, @RequestParam String role) {
        return ResponseEntity.ok(ApiResponse.success(groupMemberService.updateMemberRole(id, role)));
    }

    /**
     * Soft-removes a member from a group.
     *
     * @param id the membership id
     * @return {@code 200 OK} with a confirmation message
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable Long id) {
        groupMemberService.removeMember(id);
        return ResponseEntity.ok(ApiResponse.success("Member removed", null));
    }
}
