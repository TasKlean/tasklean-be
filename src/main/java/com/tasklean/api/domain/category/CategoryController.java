package com.tasklean.api.domain.category;

import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.category.dto.CategoryRequest;
import com.tasklean.api.domain.category.dto.CategoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for task categories — create, fetch, list by group, update, and delete.
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Creates a category.
     *
     * @param request the category details (name, color, icon, group)
     * @return {@code 201 Created} with the created category
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(categoryService.createCategory(request)));
    }

    /**
     * Fetches a category by id.
     *
     * @param id the category id
     * @return {@code 200 OK} with the category
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getCategoryById(id)));
    }

    /**
     * Lists a group's active categories.
     *
     * @param groupId the group id
     * @return {@code 200 OK} with the categories
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategoriesByGroup(@RequestParam Long groupId) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getCategoriesByGroup(groupId)));
    }

    /**
     * Updates a category.
     *
     * @param id      the category id
     * @param request the new category details
     * @return {@code 200 OK} with the updated category
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.updateCategory(id, request)));
    }

    /**
     * Soft-deletes a category.
     *
     * @param id the category id
     * @return {@code 200 OK} with a confirmation message
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted", null));
    }
}
