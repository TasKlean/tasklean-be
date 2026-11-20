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
 *
 * <p>An upload carries its target as plain ids, so nothing here loads another row or walks an
 * association - which matters because a resolver runs before the call, with no session open.
 */
@Component("uploadSecurity")
@RequiredArgsConstructor
public class UploadSecurity {

    private final UploadRepository uploadRepository;
    private final GroupSecurity groupSecurity;
    private final TaskSecurity taskSecurity;

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
            // Nobody uploads a picture onto somebody else's profile, whatever their group role.
            case USER_PHOTO -> request.getTargetUid().equals(currentUserUid());
            // Any member may add a chore, so any member may sign a picture for one. Whether they
            // may put it on a *particular* task is settled at attach time, by the right to edit it.
            case TASK_PHOTO -> groupSecurity.isMember(request.getTargetUid());
            // The target is the task, so this is membership of its household. That the caller is
            // the one who may record the round is settled at attach time, as the assignee rule.
            case COMPLETION_PHOTO -> taskSecurity.canView(request.getTargetUid());
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
                        : visibleWhereItHangs(upload))
                .orElse(false);
    }

    // Once attached, an image is exactly as visible as what it belongs to - and that differs per
    // purpose, so a new one has to answer here rather than inherit somebody else's audience.
    private boolean visibleWhereItHangs(Upload upload) {
        return switch (upload.getPurpose()) {
            case GROUP_PHOTO, TASK_PHOTO, COMPLETION_PHOTO -> sharesGroup(upload);
            // An avatar is rendered on rosters, member pickers and the trail, so it is readable by
            // the household rather than by its owner alone - but by nobody further out.
            case USER_PHOTO -> isRequester(upload)
                    || groupSecurity.sharesAnyGroupWith(upload.getUserId());
        };
    }

    private boolean isRequester(Upload upload) {
        Long userId = currentUserId();
        return userId != null && userId.equals(upload.getUserId());
    }

    private boolean sharesGroup(Upload upload) {
        // Null-checked first, always: an upload whose household is gone has to be readable by
        // nobody rather than by anybody holding its uid.
        return upload.getGroupId() != null
                && groupSecurity.isMemberOfGroup(upload.getGroupId());
    }

    private String currentUserUid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal principal ? principal.uid() : null;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal principal ? principal.userId() : null;
    }
}
