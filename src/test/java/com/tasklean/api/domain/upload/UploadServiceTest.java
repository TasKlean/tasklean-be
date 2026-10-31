package com.tasklean.api.domain.upload;

import com.tasklean.api.common.exception.InvalidRequestException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.common.storage.ObjectStorage;
import com.tasklean.api.common.storage.StoredObject;
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

import java.time.Clock;
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
    private static final String KEY = "group-photo/x.png";

    @BeforeEach
    void setUp() {
        storageProperties.setUploadTtl(Duration.ofMinutes(5));
        storageProperties.setDownloadTtl(Duration.ofMinutes(15));
        uploadService = new UploadService(uploadRepository, groupRepository, userRepository,
                objectStorage, storageProperties, Clock.systemUTC());
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

    // --- attachGroupPhoto ---

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

    private Group targetGroup(String currentPhotoUid) {
        return Group.builder().idGroup(GROUP_ID).uid(GROUP_UID).photoUid(currentPhotoUid).build();
    }

    private Upload groupPhoto(Long requesterId, UploadStatus status) {
        return Upload.builder()
                .uid(UPLOAD_UID)
                .storageKey(KEY)
                .purpose(UploadPurpose.GROUP_PHOTO)
                .contentType("image/png")
                .sizeBytes(12L)
                .status(status)
                .user(User.builder().idUser(requesterId).build())
                .group(Group.builder().idGroup(GROUP_ID).uid(GROUP_UID).build())
                .build();
    }

    private void stubPending(Long requesterId) {
        when(uploadRepository.findByUid(UPLOAD_UID))
                .thenReturn(Optional.of(groupPhoto(requesterId, UploadStatus.PENDING)));
    }

    private void stubObjectAsDeclared() {
        when(objectStorage.head(KEY)).thenReturn(Optional.of(new StoredObject("image/png", 12L)));
        when(objectStorage.readPrefix(KEY, 12)).thenReturn(Optional.of(PNG_BYTES));
    }

    @Test
    void attachGroupPhoto_ownPendingUploadThatArrived_isAttached() {
        Upload upload = groupPhoto(USER_ID, UploadStatus.PENDING);
        when(uploadRepository.findByUid(UPLOAD_UID)).thenReturn(Optional.of(upload));
        stubObjectAsDeclared();

        String stored = uploadService.attachGroupPhoto(UPLOAD_UID, targetGroup(null), USER_ID);

        assertThat(stored).isEqualTo(UPLOAD_UID);
        assertThat(upload.getStatus()).isEqualTo(UploadStatus.ATTACHED);
        assertThat(upload.getDateAttached()).isNotNull();
    }

    @Test
    void attachGroupPhoto_null_clearsThePhotoAndSupersedesTheOldOne() {
        Upload previous = groupPhoto(USER_ID, UploadStatus.ATTACHED);
        when(uploadRepository.findByUid("old-uid")).thenReturn(Optional.of(previous));

        String stored = uploadService.attachGroupPhoto(null, targetGroup("old-uid"), USER_ID);

        assertThat(stored).isNull();
        assertThat(previous.getStatus()).isEqualTo(UploadStatus.SUPERSEDED);
    }

    @Test
    void attachGroupPhoto_replacingAPhoto_supersedesTheOldOne() {
        Upload previous = groupPhoto(USER_ID, UploadStatus.ATTACHED);
        Upload replacement = groupPhoto(USER_ID, UploadStatus.PENDING);
        when(uploadRepository.findByUid("old-uid")).thenReturn(Optional.of(previous));
        when(uploadRepository.findByUid(UPLOAD_UID)).thenReturn(Optional.of(replacement));
        stubObjectAsDeclared();

        uploadService.attachGroupPhoto(UPLOAD_UID, targetGroup("old-uid"), USER_ID);

        // Nothing points at the replaced object any more, so the sweep may collect it.
        assertThat(previous.getStatus()).isEqualTo(UploadStatus.SUPERSEDED);
        assertThat(replacement.getStatus()).isEqualTo(UploadStatus.ATTACHED);
    }

    @Test
    void attachGroupPhoto_thePhotoTheGroupAlreadyHas_changesNothing() {
        // A repeated update must not fail just because the upload is no longer pending.
        String stored = uploadService.attachGroupPhoto(UPLOAD_UID, targetGroup(UPLOAD_UID), USER_ID);

        assertThat(stored).isEqualTo(UPLOAD_UID);
        verify(uploadRepository, never()).save(any());
        verify(objectStorage, never()).head(anyString());
    }

    @Test
    void attachGroupPhoto_unknownUid_throwsInvalidRequest() {
        when(uploadRepository.findByUid(UPLOAD_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uploadService.attachGroupPhoto(UPLOAD_UID, targetGroup(null), USER_ID))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void attachGroupPhoto_someoneElsesUpload_throwsInvalidRequest() {
        stubPending(2L);

        assertThatThrownBy(() -> uploadService.attachGroupPhoto(UPLOAD_UID, targetGroup(null), USER_ID))
                .isInstanceOf(InvalidRequestException.class);
        verify(objectStorage, never()).head(anyString());
    }

    @Test
    void attachGroupPhoto_uploadForAnotherGroup_throwsInvalidRequest() {
        Upload elsewhere = groupPhoto(USER_ID, UploadStatus.PENDING);
        elsewhere.setGroup(Group.builder().idGroup(99L).uid("other").build());
        when(uploadRepository.findByUid(UPLOAD_UID)).thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() -> uploadService.attachGroupPhoto(UPLOAD_UID, targetGroup(null), USER_ID))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void attachGroupPhoto_anUploadAlreadySpent_throwsInvalidRequest() {
        when(uploadRepository.findByUid(UPLOAD_UID))
                .thenReturn(Optional.of(groupPhoto(USER_ID, UploadStatus.ATTACHED)));

        assertThatThrownBy(() -> uploadService.attachGroupPhoto(UPLOAD_UID, targetGroup(null), USER_ID))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void attachGroupPhoto_objectNeverArrived_throwsInvalidRequest() {
        stubPending(USER_ID);
        when(objectStorage.head(KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uploadService.attachGroupPhoto(UPLOAD_UID, targetGroup(null), USER_ID))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void attachGroupPhoto_objectIsADifferentSize_throwsInvalidRequest() {
        stubPending(USER_ID);
        when(objectStorage.head(KEY)).thenReturn(Optional.of(new StoredObject("image/png", 9999L)));

        assertThatThrownBy(() -> uploadService.attachGroupPhoto(UPLOAD_UID, targetGroup(null), USER_ID))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void attachGroupPhoto_objectIsADifferentType_throwsInvalidRequest() {
        stubPending(USER_ID);
        when(objectStorage.head(KEY)).thenReturn(Optional.of(new StoredObject("image/jpeg", 12L)));

        assertThatThrownBy(() -> uploadService.attachGroupPhoto(UPLOAD_UID, targetGroup(null), USER_ID))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void attachGroupPhoto_bytesAreNotTheDeclaredImage_throwsInvalidRequest() {
        // The header said PNG and the bucket stored that header; only the bytes give it away.
        stubPending(USER_ID);
        when(objectStorage.head(KEY)).thenReturn(Optional.of(new StoredObject("image/png", 12L)));
        when(objectStorage.readPrefix(KEY, 12)).thenReturn(Optional.of("<?php echo 1;".getBytes()));

        assertThatThrownBy(() -> uploadService.attachGroupPhoto(UPLOAD_UID, targetGroup(null), USER_ID))
                .isInstanceOf(InvalidRequestException.class);
    }
}
