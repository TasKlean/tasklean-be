package com.tasklean.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Binds the {@code storage.*} properties: which S3-compatible bucket holds uploaded images, how to
 * authenticate to it, and how long a signed URL stays valid. Which bucket it is (Cloudflare R2 in
 * production, LocalStack in dev) is entirely env config — no code reads the environment.
 */
@Configuration
@ConfigurationProperties(prefix = "storage")
@Getter
@Setter
public class StorageProperties {

    private String endpoint;
    private String region;
    private String bucket;
    private String accessKey;
    private String secretKey;

    /**
     * Addresses the bucket as {@code endpoint/bucket/key} rather than {@code bucket.endpoint/key}.
     * LocalStack needs it; R2 serves either shape.
     */
    private boolean pathStyle;

    /** How long an upload URL is valid — long enough to send a photo, short enough to not be a key. */
    private Duration uploadTtl = Duration.ofMinutes(5);

    /** How long a download URL is valid. Shorter: the frontend re-asks for one per page view. */
    private Duration downloadTtl = Duration.ofMinutes(15);
}
