package com.tasklean.api.domain.upload;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.common.storage.ObjectStorage;
import com.tasklean.api.config.StorageProperties;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.upload.dto.SignedUrlResponse;
import com.tasklean.api.domain.upload.dto.UploadRequest;
import com.tasklean.api.domain.upload.dto.UploadResponse;
import com.tasklean.api.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Hands out signed URLs for uploaded images, and keeps the record of what was signed for. Image
 * bytes never reach this process: the browser PUTs them to the bucket and reads them back from it.
 *
 * <p>A signed URL is a credential, so one is never logged — only the uid it belongs to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {

    private final UploadRepository uploadRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final ObjectStorage objectStorage;
    private final StorageProperties storageProperties;

    /**
     * Records an intended upload and signs the URL it may be written to. The type and size are part
     * of the signature, so the bucket refuses bytes that differ from what was asked for.
     *
     * @param request      what the image is for, its type and its exact size
     * @param callerUserId the id of the authenticated caller
     * @return the upload's uid and the URL to PUT to
     * @throws ResourceNotFoundException if the target does not exist
     */
    @Transactional
    public UploadResponse createUpload(UploadRequest request, Long callerUserId) {
        Group group = requireGroup(request.getTargetUid());

        String uid = UUID.randomUUID().toString();
        // The key is derived from the uid, so there is one opaque identifier rather than two.
        String storageKey = request.getPurpose().keyPrefix() + "/" + uid + extension(request.getContentType());

        Upload upload = uploadRepository.save(Upload.builder()
                .uid(uid)
                .storageKey(storageKey)
                .purpose(request.getPurpose())
                .contentType(request.getContentType())
                .sizeBytes(request.getSizeBytes())
                .status(UploadStatus.PENDING)
                .user(userRepository.getReferenceById(callerUserId))
                .group(group)
                .build());

        log.info("Upload signed: uid={} purpose={} groupUid={} bytes={}",
                upload.getUid(), upload.getPurpose(), group.getUid(), upload.getSizeBytes());

        return UploadResponse.builder()
                .uid(upload.getUid())
                .uploadUrl(objectStorage.presignUpload(
                        storageKey, request.getContentType(), request.getSizeBytes()))
                .expiresInSeconds(storageProperties.getUploadTtl().toSeconds())
                .build();
    }

    /**
     * Signs a short-lived URL the upload's image can be read from.
     *
     * @param uid the upload's public uid
     * @return the URL and how long it stays valid
     * @throws ResourceNotFoundException if no upload has that uid
     */
    public SignedUrlResponse downloadUrl(String uid) {
        Upload upload = uploadRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.UPLOAD_NOT_FOUND));
        return SignedUrlResponse.builder()
                .url(objectStorage.presignDownload(upload.getStorageKey()))
                .expiresInSeconds(storageProperties.getDownloadTtl().toSeconds())
                .build();
    }

    private Group requireGroup(String groupUid) {
        return groupRepository.findByUid(groupUid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));
    }

    // The type is already narrowed to the allowlist by validation, so this cannot fall through.
    private String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".webp";
        };
    }
}
