package com.tasklean.api.common.storage;

import com.tasklean.api.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3ObjectStorageTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    // Not a mock: the bucket name and the TTLs it carries are what the requests are built from.
    private final StorageProperties properties = new StorageProperties();

    private S3ObjectStorage storage;

    private static final String KEY = "group-photo/abc.png";
    private static final String BUCKET = "tasklean-test";

    @BeforeEach
    void setUp() {
        properties.setBucket(BUCKET);
        properties.setUploadTtl(Duration.ofMinutes(5));
        properties.setDownloadTtl(Duration.ofMinutes(15));
        storage = new S3ObjectStorage(s3Client, s3Presigner, properties);
    }

    private void stubPutPresign() throws Exception {
        PresignedPutObjectRequest signed = mock(PresignedPutObjectRequest.class);
        when(signed.url()).thenReturn(URI.create("https://bucket.example/put").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(signed);
    }

    private ResponseInputStream<GetObjectResponse> objectOf(byte[] bytes) {
        return new ResponseInputStream<>(GetObjectResponse.builder().build(),
                new ByteArrayInputStream(bytes));
    }

    @Test
    void presignUpload_signsTheExactTypeAndLength() throws Exception {
        stubPutPresign();

        storage.presignUpload(KEY, "image/png", 2048L);

        // The cap is only enforced by the bucket because both are inside the signature; dropping
        // either would silently move that enforcement to nowhere.
        ArgumentCaptor<PutObjectPresignRequest> request =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(request.capture());
        assertThat(request.getValue().putObjectRequest().contentType()).isEqualTo("image/png");
        assertThat(request.getValue().putObjectRequest().contentLength()).isEqualTo(2048L);
    }

    @Test
    void presignUpload_usesTheBucketAndKeyAndUploadTtl() throws Exception {
        stubPutPresign();

        String url = storage.presignUpload(KEY, "image/png", 2048L);

        ArgumentCaptor<PutObjectPresignRequest> request =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(request.capture());
        assertThat(request.getValue().putObjectRequest().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().putObjectRequest().key()).isEqualTo(KEY);
        assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(url).isEqualTo("https://bucket.example/put");
    }

    @Test
    void presignDownload_signsTheKeyForTheDownloadTtl() throws Exception {
        PresignedGetObjectRequest signed = mock(PresignedGetObjectRequest.class);
        when(signed.url()).thenReturn(URI.create("https://bucket.example/get").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);

        String url = storage.presignDownload(KEY);

        ArgumentCaptor<GetObjectPresignRequest> request =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(request.capture());
        assertThat(request.getValue().getObjectRequest().key()).isEqualTo(KEY);
        assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(url).isEqualTo("https://bucket.example/get");
    }

    @Test
    void head_existingObject_reportsItsTypeAndLength() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentType("image/png").contentLength(2048L).build());

        Optional<StoredObject> stored = storage.head(KEY);

        assertThat(stored).contains(new StoredObject("image/png", 2048L));
    }

    @Test
    void head_missingObject_isEmptyRatherThanThrowing() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        // The attach path reads this as "the upload never arrived", so it must not be an exception.
        assertThat(storage.head(KEY)).isEmpty();
    }

    @Test
    void readPrefix_asksForOnlyTheLeadingBytes() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(objectOf(new byte[]{1, 2, 3}));

        storage.readPrefix(KEY, 12);

        // Inclusive, so 12 bytes is 0-11: an off-by-one here breaks the magic-number check.
        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(request.capture());
        assertThat(request.getValue().range()).isEqualTo("bytes=0-11");
    }

    @Test
    void readPrefix_existingObject_returnsTheBytesRead() {
        byte[] prefix = {(byte) 0x89, 'P', 'N', 'G'};
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(objectOf(prefix));

        assertThat(storage.readPrefix(KEY, 12)).contains(prefix);
    }

    @Test
    void readPrefix_missingObject_isEmptyRatherThanThrowing() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        assertThat(storage.readPrefix(KEY, 12)).isEmpty();
    }

    @Test
    void readPrefix_readFails_throwsRatherThanLookingLikeAMissingObject() {
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(
                new ResponseInputStream<>(GetObjectResponse.builder().build(), new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("connection reset");
                    }
                }));

        // Empty would mean "never uploaded", which would turn a bucket glitch into a 400 the
        // caller cannot fix by uploading again.
        assertThatThrownBy(() -> storage.readPrefix(KEY, 12)).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void delete_removesTheKeyFromTheConfiguredBucket() {
        storage.delete(KEY);

        ArgumentCaptor<DeleteObjectRequest> request =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo(KEY);
    }
}
