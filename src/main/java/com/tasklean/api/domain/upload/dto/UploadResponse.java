package com.tasklean.api.domain.upload.dto;

import lombok.*;

/**
 * A signed upload. The caller PUTs the bytes straight to {@code uploadUrl} with exactly the
 * content type and length it asked for, then sends {@code uid} on the entity it belongs to.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadResponse {

    private String uid;
    private String uploadUrl;
    private long expiresInSeconds;
}
