package com.tasklean.api.domain.group;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.group.dto.GroupRequest;
import com.tasklean.api.domain.group.dto.GroupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(@Valid @RequestBody GroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(groupService.createGroup(request)));
    }

    @GetMapping("/{uid}")
    public ResponseEntity<ApiResponse<GroupResponse>> getGroup(@PathVariable String uid) {
        return ResponseEntity.ok(ApiResponse.success(groupService.getGroupByUid(uid)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GroupResponse>>> getAllGroups() {
        return ResponseEntity.ok(ApiResponse.success(groupService.getAllGroups()));
    }

    @PutMapping("/{uid}")
    public ResponseEntity<ApiResponse<GroupResponse>> updateGroup(
            @PathVariable String uid, @Valid @RequestBody GroupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(groupService.updateGroup(uid, request)));
    }

    @DeleteMapping("/{uid}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable String uid) {
        groupService.deleteGroup(uid);
        return ResponseEntity.ok(ApiResponse.success("Group deleted", null));
    }
}
