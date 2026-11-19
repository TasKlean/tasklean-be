package com.tasklean.api.domain.tag;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.tag.dto.TagCreateRequest;
import com.tasklean.api.domain.tag.dto.TagQueryRequest;
import com.tasklean.api.domain.tag.dto.TagResponse;
import com.tasklean.api.domain.tag.dto.TagUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for a household's tags. Any member may add one — a tag is a label somebody found
 * useful — but renaming or removing one changes what everybody else sees, so that needs an admin.
 */
// Fully qualified: this package also declares a Tag entity, and an import of
// Swagger's Tag would silently shadow it for readers of this file.
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tags", description = "Group-scoped task tags.")
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /**
     * Creates a tag in a household the caller belongs to, or brings back the deleted one that
     * already holds the name.
     *
     * @param request the tag's details and the household it belongs to
     * @return {@code 201 Created} with the created tag
     */
    @PreAuthorize("@groupSecurity.isMember(#request.groupUid)")
    @PostMapping
    public ResponseEntity<ApiResponse<TagResponse>> createTag(@Valid @RequestBody TagCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(tagService.createTag(request)));
    }

    /**
     * Fetches a tag by id.
     *
     * @param id the tag id
     * @return {@code 200 OK} with the tag
     */
    @PreAuthorize("@tagSecurity.canView(#id)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TagResponse>> getTag(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tagService.getTagById(id)));
    }

    /**
     * Lists a household's tags, ordered by name and optionally narrowed by a search — this is
     * what the frontend suggests from as a member types a label.
     *
     * @param query the household and the text to match
     * @return {@code 200 OK} with the matching tags
     */
    @PreAuthorize("@groupSecurity.isMember(#query.groupUid)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TagResponse>>> getTags(
            @Valid @ParameterObject TagQueryRequest query) {
        return ResponseEntity.ok(ApiResponse.success(tagService.searchTags(query)));
    }

    /**
     * Updates a tag.
     *
     * @param id      the tag id
     * @param request the new details
     * @return {@code 200 OK} with the updated tag
     */
    @PreAuthorize("@tagSecurity.canManage(#id)")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TagResponse>> updateTag(
            @PathVariable Long id, @Valid @RequestBody TagUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(tagService.updateTag(id, request)));
    }

    /**
     * Soft-deletes a tag.
     *
     * @param id the tag id
     * @return {@code 200 OK} with a confirmation message
     */
    @PreAuthorize("@tagSecurity.canManage(#id)")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return ResponseEntity.ok(ApiResponse.success("Tag deleted", null));
    }
}
