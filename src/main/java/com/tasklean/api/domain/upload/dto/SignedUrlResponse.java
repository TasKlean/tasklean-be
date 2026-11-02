package com.tasklean.api.domain.upload.dto;

import lombok.*;

/**
 * A short-lived URL an uploaded image can be read from. It is a credential, so it is handed to an
 * authorized caller per request rather than stored anywhere.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignedUrlResponse {

    private String url;
    private long expiresInSeconds;
}
