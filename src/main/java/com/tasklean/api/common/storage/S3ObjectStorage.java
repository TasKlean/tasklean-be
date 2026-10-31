package com.tasklean.api.common.storage;

import com.tasklean.api.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * The {@link ObjectStorage} port over any S3-compatible bucket — Cloudflare R2 in production,
 * LocalStack in dev, told apart only by {@code storage.*} config.
 *
 * <p>A signed URL is a credential: it carries the authority to write or read one object for as
 * long as it lives, so one is never logged, only the key it was signed for.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    @Override
    public String presignUpload(String key, String contentType, long contentLength) {
        // Both are part of the signature, so the bucket itself refuses a mismatched upload -
        // the size cap is enforced before any bytes reach us, not after.
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        String url = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(properties.getUploadTtl())
                .putObjectRequest(put)
                .build()).url().toString();
        log.debug("Signed an upload URL: key={}", key);
        return url;
    }

    @Override
    public String presignDownload(String key) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build();

        String url = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(properties.getDownloadTtl())
                .getObjectRequest(get)
                .build()).url().toString();
        log.debug("Signed a download URL: key={}", key);
        return url;
    }

    @Override
    public Optional<StoredObject> head(String key) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
            return Optional.of(new StoredObject(response.contentType(), response.contentLength()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<byte[]> readPrefix(String key, int count) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                // A ranged read, so checking a magic number never downloads a whole photo.
                .range("bytes=0-" + (count - 1))
                .build();

        try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(get)) {
            return Optional.of(stream.readAllBytes());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read object prefix: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build());
        log.info("Deleted a stored object: key={}", key);
    }
}
