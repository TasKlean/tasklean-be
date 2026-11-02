package com.tasklean.api.domain.upload;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.common.ApiResponse;
import com.tasklean.api.domain.upload.dto.SignedUrlResponse;
import com.tasklean.api.domain.upload.dto.UploadRequest;
import com.tasklean.api.domain.upload.dto.UploadResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for uploaded images: ask for a signed upload, and ask for a signed URL to read
 * one back. The bytes themselves go browser-to-bucket and never pass through this API.
 */
@Tag(name = "Uploads", description = "Signed URLs for uploading an image to the bucket and reading it back.")
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    /**
     * Signs an upload for one image. Who may ask depends on what the image is for — a group photo
     * needs that group's admin.
     *
     * @param request   what the image is for, its content type and its exact size in bytes
     * @param principal the authenticated caller
     * @return {@code 201 Created} with the upload's uid and the URL to PUT the bytes to
     */
    @PreAuthorize("hasRole('SUPER_ADMIN') or @uploadSecurity.mayCreate(#request)")
    @PostMapping
    public ResponseEntity<ApiResponse<UploadResponse>> createUpload(
            @Valid @RequestBody UploadRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(uploadService.createUpload(request, principal.userId())));
    }

    /**
     * Signs a short-lived URL for reading an uploaded image, for a caller allowed to see the thing
     * it belongs to.
     *
     * @param uid the upload's public uid
     * @return {@code 200 OK} with the URL and how long it stays valid
     */
    @PreAuthorize("hasRole('ADMIN') or @uploadSecurity.mayRead(#uid)")
    @GetMapping("/{uid}")
    public ResponseEntity<ApiResponse<SignedUrlResponse>> getUrl(@PathVariable String uid) {
        return ResponseEntity.ok(ApiResponse.success(uploadService.downloadUrl(uid)));
    }
}
