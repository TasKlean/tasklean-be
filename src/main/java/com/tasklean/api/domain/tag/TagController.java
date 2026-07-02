package com.tasklean.api.domain.tag;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.tag.dto.TagRequest;
import com.tasklean.api.domain.tag.dto.TagResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @PostMapping
    public ResponseEntity<ApiResponse<TagResponse>> createTag(@Valid @RequestBody TagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(tagService.createTag(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TagResponse>> getTag(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tagService.getTagById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TagResponse>>> getTagsByGroup(@RequestParam Long groupId) {
        return ResponseEntity.ok(ApiResponse.success(tagService.getTagsByGroup(groupId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TagResponse>> updateTag(
            @PathVariable Long id, @Valid @RequestBody TagRequest request) {
        return ResponseEntity.ok(ApiResponse.success(tagService.updateTag(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return ResponseEntity.ok(ApiResponse.success("Tag deleted", null));
    }
}
