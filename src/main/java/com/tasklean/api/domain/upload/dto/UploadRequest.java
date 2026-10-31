package com.tasklean.api.domain.upload.dto;

import com.tasklean.api.common.validation.FieldLimits;
import com.tasklean.api.domain.upload.UploadPurpose;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UploadRequest {

    /** What the image is for; it decides who may ask for the upload. */
    @NotNull
    private UploadPurpose purpose;

    /** The uid of what the image is for — the group, for a group photo. */
    @NotBlank
    @Size(min = 1, max = FieldLimits.UID)
    private String targetUid;

    /**
     * The image's type. JPEG, PNG and WebP only, and anything else is refused before a signature
     * exists — no SVG, which is a script container rather than a photo.
     */
    @NotBlank
    @Pattern(regexp = "image/(jpeg|png|webp)")
    @Size(min = 1, max = FieldLimits.CONTENT_TYPE)
    private String contentType;

    /** The exact byte count the upload will send; it is signed, so it has to be known up front. */
    @NotNull
    @Positive
    @Max(FieldLimits.IMAGE_BYTES)
    private Long sizeBytes;
}
