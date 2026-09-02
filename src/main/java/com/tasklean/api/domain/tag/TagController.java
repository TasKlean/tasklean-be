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

/**
 * REST endpoints for tags — create, fetch, list by group, update, and delete.
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /**
     * Creates a tag.
     *
     * @param request the tag details (name, color, group)
     * @return {@code 201 Created} with the created tag
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TagResponse>> createTag(@Valid @RequestBody TagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(tagService.createTag(request)));
    }

    /**
     * Fetches a tag by id.
     *
     * @param id the tag id
     * @return {@code 200 OK} with the tag
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TagResponse>> getTag(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tagService.getTagById(id)));
    }

    /**
     * Lists a group's active tags.
     *
     * @param groupId the group id
     * @return {@code 200 OK} with the tags
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TagResponse>>> getTagsByGroup(@RequestParam Long groupId) {
        return ResponseEntity.ok(ApiResponse.success(tagService.getTagsByGroup(groupId)));
    }

    /**
     * Updates a tag.
     *
     * @param id      the tag id
     * @param request the new tag details
     * @return {@code 200 OK} with the updated tag
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TagResponse>> updateTag(
            @PathVariable Long id, @Valid @RequestBody TagRequest request) {
        return ResponseEntity.ok(ApiResponse.success(tagService.updateTag(id, request)));
    }

    /**
     * Soft-deletes a tag.
     *
     * @param id the tag id
     * @return {@code 200 OK} with a confirmation message
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return ResponseEntity.ok(ApiResponse.success("Tag deleted", null));
    }
}
