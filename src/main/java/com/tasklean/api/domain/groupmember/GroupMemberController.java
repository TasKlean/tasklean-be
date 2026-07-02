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

@RestController
@RequestMapping("/api/group-members")
@RequiredArgsConstructor
public class GroupMemberController {

    private final GroupMemberService groupMemberService;

    @PostMapping
    public ResponseEntity<ApiResponse<GroupMemberResponse>> addMember(@Valid @RequestBody GroupMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(groupMemberService.addMember(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupMemberResponse>> getMember(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(groupMemberService.getMemberById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GroupMemberResponse>>> getMembersByGroup(@RequestParam Long groupId) {
        return ResponseEntity.ok(ApiResponse.success(groupMemberService.getMembersByGroup(groupId)));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<GroupMemberResponse>> updateRole(
            @PathVariable Long id, @RequestParam String role) {
        return ResponseEntity.ok(ApiResponse.success(groupMemberService.updateMemberRole(id, role)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable Long id) {
        groupMemberService.removeMember(id);
        return ResponseEntity.ok(ApiResponse.success("Member removed", null));
    }
}
