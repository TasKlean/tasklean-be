package com.tasklean.api.security;

import com.tasklean.api.auth.jwt.AuthPrincipal;
import com.tasklean.api.domain.upload.Upload;
import com.tasklean.api.domain.upload.UploadRepository;
import com.tasklean.api.domain.upload.UploadStatus;
import com.tasklean.api.domain.upload.dto.UploadRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Authorization for uploaded images, referenced as {@code @uploadSecurity} from
 * {@code @PreAuthorize}. An image is never its own authorization boundary — the thing it belongs
 * to is — so every check here resolves that target and asks {@link GroupSecurity} about it.
 */
@Component("uploadSecurity")
@RequiredArgsConstructor
public class UploadSecurity {

    private final UploadRepository uploadRepository;
    private final GroupSecurity groupSecurity;

    /**
     * Whether the caller may ask for this upload. Signing is refused unless the caller could
     * actually use the result, so a signature is never handed out speculatively.
     *
     * @param request the requested upload
     * @return {@code true} if the caller may upload an image of that purpose for that target
     */
    public boolean mayCreate(UploadRequest request) {
        if (request == null || request.getPurpose() == null || request.getTargetUid() == null) {
            return false;
        }
        return switch (request.getPurpose()) {
            // Changing a household's picture is a group-admin act, so asking for one is too.
            case GROUP_PHOTO -> groupSecurity.isAdmin(request.getTargetUid());
        };
    }

    /**
     * Whether the caller may read this upload back. A pending one is visible only to whoever asked
     * for it; once attached, it is visible to anyone who may see the group it belongs to.
     *
     * @param uid the upload's public uid
     * @return {@code true} if the caller may read the image
     */
    public boolean mayRead(String uid) {
        return uploadRepository.findByUid(uid)
                .map(upload -> upload.getStatus() == UploadStatus.PENDING
                        ? isRequester(upload)
                        : sharesGroup(upload))
                .orElse(false);
    }

    private boolean isRequester(Upload upload) {
        Long userId = currentUserId();
        return userId != null && userId.equals(upload.getUser().getIdUser());
    }

    private boolean sharesGroup(Upload upload) {
        return upload.getGroup() != null
                && groupSecurity.isMemberOfGroup(upload.getGroup().getIdGroup());
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal principal ? principal.userId() : null;
    }
}
