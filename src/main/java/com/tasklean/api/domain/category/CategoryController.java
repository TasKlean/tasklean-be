package com.tasklean.api.domain.category;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.category.dto.CategoryCreateRequest;
import com.tasklean.api.domain.category.dto.CategoryResponse;
import com.tasklean.api.domain.category.dto.CategoryUpdateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for a household's task categories. Every member reads them; only a group admin
 * adds, renames or removes one, because the filing system belongs to the household rather than to
 * whoever happened to set it up.
 */
@Tag(name = "Categories", description = "Group-scoped task categories.")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Creates a category in a household the caller administers.
     *
     * @param request the category's details and the household it belongs to
     * @return {@code 201 Created} with the created category
     */
    @PreAuthorize("@groupSecurity.isAdmin(#request.groupUid)")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CategoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(categoryService.createCategory(request)));
    }

    /**
     * Fetches a category by id.
     *
     * @param id the category id
     * @return {@code 200 OK} with the category
     */
    @PreAuthorize("@categorySecurity.canView(#id)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getCategoryById(id)));
    }

    /**
     * Updates a category.
     *
     * @param id      the category id
     * @param request the new details
     * @return {@code 200 OK} with the updated category
     */
    @PreAuthorize("@categorySecurity.canManage(#id)")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.updateCategory(id, request)));
    }

    /**
     * Soft-deletes a category no task is filed under.
     *
     * @param id the category id
     * @return {@code 200 OK} with a confirmation message
     */
    @PreAuthorize("@categorySecurity.canManage(#id)")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted", null));
    }
}
