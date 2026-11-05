package com.tasklean.api.domain.upload;

import com.tasklean.api.common.ErrorCodes;
import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.InvalidRequestException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.common.storage.ObjectStorage;
import com.tasklean.api.common.storage.StoredObject;
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

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
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
    private final Clock clock;

    /**
     * Records an intended upload and signs the URL it may be written to. The type and size are part
     * of the signature, so the bucket refuses bytes that differ from what was asked for.
     *
     * @param request      what the image is for, its type and its exact size
     * @param callerUserId the id of the authenticated caller
     * @return the upload's uid and the URL to PUT to
     * @throws ResourceNotFoundException if the target does not exist or has been deleted
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

    /**
     * Points a group at an uploaded photo, after proving the object is really there and is really
     * what was declared. The bucket cannot be trusted to have refused a mismatch — a local one does
     * not even try — so the checks happen here, before anything stored points at the object.
     *
     * <p>Re-sending the photo a group already has is a no-op, so a repeated update is harmless.
     *
     * @param uploadUid    the upload's public uid, or {@code null} to clear the photo
     * @param group        the group the photo belongs to
     * @param callerUserId the id of the authenticated caller
     * @return the uid to store on the group, or {@code null} when the photo is being cleared
     * @throws InvalidRequestException if the upload is not this caller's pending group photo for
     *                                 this group, or its object does not match what was declared
     */
    @Transactional
    public String attachGroupPhoto(String uploadUid, Group group, Long callerUserId) {
        if (uploadUid == null) {
            supersedeCurrentPhoto(group, null);
            return null;
        }
        if (uploadUid.equals(group.getPhotoUid())) {
            return uploadUid;
        }

        Upload upload = uploadRepository.findByUid(uploadUid)
                .orElseThrow(this::unusable);
        requireOwnPendingGroupPhoto(upload, group, callerUserId);
        requireObjectMatchesDeclaration(upload);

        supersedeCurrentPhoto(group, uploadUid);
        upload.setStatus(UploadStatus.ATTACHED);
        upload.setDateAttached(LocalDateTime.now(clock));
        uploadRepository.save(upload);
        log.info("Upload attached: uid={} purpose={} groupUid={}",
                upload.getUid(), upload.getPurpose(), group.getUid());
        return upload.getUid();
    }

    // Everything the caller claimed about the upload, checked against the row we wrote at signing.
    private void requireOwnPendingGroupPhoto(Upload upload, Group group, Long callerUserId) {
        boolean ownPendingPhotoForThisGroup = upload.getStatus() == UploadStatus.PENDING
                && upload.getPurpose() == UploadPurpose.GROUP_PHOTO
                && upload.getUser().getIdUser().equals(callerUserId)
                && upload.getGroup() != null
                && upload.getGroup().getIdGroup().equals(group.getIdGroup());
        if (!ownPendingPhotoForThisGroup) {
            throw unusable();
        }
    }

    // The object has to exist and be what was signed for, bytes included: a content type is the
    // uploader's assertion until the magic number agrees with it.
    private void requireObjectMatchesDeclaration(Upload upload) {
        StoredObject stored = objectStorage.head(upload.getStorageKey()).orElseThrow(this::unusable);
        if (stored.contentLength() != upload.getSizeBytes()
                || !upload.getContentType().equals(stored.contentType())) {
            throw unusable();
        }
        byte[] prefix = objectStorage.readPrefix(upload.getStorageKey(), ImageSignature.BYTES_NEEDED)
                .orElseThrow(this::unusable);
        if (!ImageSignature.matches(upload.getContentType(), prefix)) {
            throw unusable();
        }
    }

    /**
     * Releases a group's photo, for when the group itself is being deleted. The row is marked
     * superseded so the sweep collects the object; left attached, nothing would ever collect it.
     *
     * @param group the group being deleted
     */
    @Transactional
    public void releaseGroupPhoto(Group group) {
        supersedeCurrentPhoto(group, null);
    }

    // A replaced photo's object is nobody's any more, so the sweep collects it.
    private void supersedeCurrentPhoto(Group group, String replacementUid) {
        Optional.ofNullable(group.getPhotoUid())
                .filter(current -> !current.equals(replacementUid))
                .flatMap(uploadRepository::findByUid)
                .ifPresent(previous -> {
                    previous.setStatus(UploadStatus.SUPERSEDED);
                    uploadRepository.save(previous);
                });
    }

    // One failure for every way an attach can be refused: which check caught it is our business.
    private InvalidRequestException unusable() {
        return new InvalidRequestException(ErrorMessages.UPLOAD_NOT_USABLE, ErrorCodes.UPLOAD_NOT_USABLE);
    }

    private Group requireGroup(String groupUid) {
        return groupRepository.findByUidAndIsActiveTrue(groupUid)
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
