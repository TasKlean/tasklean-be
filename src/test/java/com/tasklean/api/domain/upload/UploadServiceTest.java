package com.tasklean.api.domain.upload;

import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.common.storage.ObjectStorage;
import com.tasklean.api.config.StorageProperties;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.upload.dto.UploadRequest;
import com.tasklean.api.domain.user.User;
import com.tasklean.api.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private UploadRepository uploadRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ObjectStorage objectStorage;

    // Not a mock: the TTLs it carries are what the response reports, so the real binding is used.
    private final StorageProperties storageProperties = new StorageProperties();

    private UploadService uploadService;

    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;
    private static final String GROUP_UID = "group-uid";
    private static final String UPLOAD_UID = "upload-uid";

    @BeforeEach
    void setUp() {
        storageProperties.setUploadTtl(Duration.ofMinutes(5));
        storageProperties.setDownloadTtl(Duration.ofMinutes(15));
        uploadService = new UploadService(uploadRepository, groupRepository, userRepository,
                objectStorage, storageProperties);
    }

    private void stubTargetAndSave() {
        when(groupRepository.findByUid(GROUP_UID))
                .thenReturn(Optional.of(Group.builder().idGroup(GROUP_ID).uid(GROUP_UID).build()));
        when(userRepository.getReferenceById(USER_ID))
                .thenReturn(User.builder().idUser(USER_ID).build());
        when(uploadRepository.save(any(Upload.class))).thenAnswer(i -> i.getArgument(0));
    }

    private UploadRequest request(String contentType, long sizeBytes) {
        UploadRequest request = new UploadRequest();
        request.setPurpose(UploadPurpose.GROUP_PHOTO);
        request.setTargetUid(GROUP_UID);
        request.setContentType(contentType);
        request.setSizeBytes(sizeBytes);
        return request;
    }

    private Upload savedUpload() {
        ArgumentCaptor<Upload> captor = ArgumentCaptor.forClass(Upload.class);
        verify(uploadRepository).save(captor.capture());
        return captor.getValue();
    }

    // --- createUpload ---

    @Test
    void createUpload_newUpload_isPendingAndKeyedByItsOwnUid() {
        stubTargetAndSave();
        when(objectStorage.presignUpload(anyString(), anyString(), anyLong())).thenReturn("https://bucket/put");

        var response = uploadService.createUpload(request("image/png", 1024L), USER_ID);

        Upload upload = savedUpload();
        assertThat(upload.getStatus()).isEqualTo(UploadStatus.PENDING);
        assertThat(upload.getUid()).isEqualTo(response.getUid()).isNotBlank();
        // One opaque identifier, not two: the key is derived from the uid the API publishes.
        assertThat(upload.getStorageKey()).isEqualTo("group-photo/" + upload.getUid() + ".png");
        assertThat(upload.getGroup().getIdGroup()).isEqualTo(GROUP_ID);
        assertThat(upload.getUser().getIdUser()).isEqualTo(USER_ID);
    }

    @Test
    void createUpload_signsForTheExactTypeAndSizeRequested() {
        stubTargetAndSave();
        when(objectStorage.presignUpload(anyString(), anyString(), anyLong())).thenReturn("https://bucket/put");

        uploadService.createUpload(request("image/jpeg", 2048L), USER_ID);

        // Both are signed, so the bucket refuses anything else before the bytes reach us.
        String key = savedUpload().getStorageKey();
        verify(objectStorage).presignUpload(key, "image/jpeg", 2048L);
    }

    @Test
    void createUpload_jpeg_isStoredWithAJpgExtension() {
        stubTargetAndSave();
        when(objectStorage.presignUpload(anyString(), anyString(), anyLong())).thenReturn("https://bucket/put");

        uploadService.createUpload(request("image/jpeg", 10L), USER_ID);

        assertThat(savedUpload().getStorageKey()).endsWith(".jpg");
    }

    @Test
    void createUpload_webp_isStoredWithAWebpExtension() {
        stubTargetAndSave();
        when(objectStorage.presignUpload(anyString(), anyString(), anyLong())).thenReturn("https://bucket/put");

        uploadService.createUpload(request("image/webp", 10L), USER_ID);

        assertThat(savedUpload().getStorageKey()).endsWith(".webp");
    }

    @Test
    void createUpload_tellsTheCallerHowLongTheUrlLives() {
        stubTargetAndSave();
        when(objectStorage.presignUpload(anyString(), anyString(), anyLong())).thenReturn("https://bucket/put");

        var response = uploadService.createUpload(request("image/png", 10L), USER_ID);

        assertThat(response.getUploadUrl()).isEqualTo("https://bucket/put");
        assertThat(response.getExpiresInSeconds()).isEqualTo(300);
    }

    @Test
    void createUpload_unknownGroup_throwsNotFoundAndSignsNothing() {
        when(groupRepository.findByUid(GROUP_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uploadService.createUpload(request("image/png", 10L), USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(uploadRepository, never()).save(any());
        verify(objectStorage, never()).presignUpload(anyString(), anyString(), anyLong());
    }

    // --- downloadUrl ---

    @Test
    void downloadUrl_knownUpload_signsItsStoredKey() {
        when(uploadRepository.findByUid(UPLOAD_UID)).thenReturn(Optional.of(
                Upload.builder().uid(UPLOAD_UID).storageKey("group-photo/x.png").build()));
        when(objectStorage.presignDownload("group-photo/x.png")).thenReturn("https://bucket/get");

        var response = uploadService.downloadUrl(UPLOAD_UID);

        assertThat(response.getUrl()).isEqualTo("https://bucket/get");
        assertThat(response.getExpiresInSeconds()).isEqualTo(900);
    }

    @Test
    void downloadUrl_unknownUid_throwsNotFound() {
        when(uploadRepository.findByUid(UPLOAD_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uploadService.downloadUrl(UPLOAD_UID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
