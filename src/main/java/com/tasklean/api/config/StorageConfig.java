package com.tasklean.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Builds the two S3 beans the {@code common/storage} adapter uses: a client for metadata calls
 * (does the object exist, what are its first bytes, delete it) and a presigner for the URLs the
 * browser uses directly. Both are immutable and thread-safe, so one of each serves the whole app.
 */
@Configuration
@RequiredArgsConstructor
public class StorageConfig {

    private final StorageProperties properties;

    /**
     * Creates the S3 client used for object metadata and deletion.
     *
     * @return a configured {@link S3Client} pointed at the bucket's endpoint
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyle())
                        .build())
                .build();
    }

    /**
     * Creates the presigner that signs upload and download URLs.
     *
     * @return a configured {@link S3Presigner} pointed at the bucket's endpoint
     */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyle())
                        .build())
                .build();
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
    }
}
