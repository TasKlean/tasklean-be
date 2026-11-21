package com.tasklean.api.domain.user;

import com.tasklean.api.auth.refresh.RefreshTokenService;
import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditChanges;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.groupmember.MembershipClosure;
import com.tasklean.api.domain.upload.UploadService;
import com.tasklean.api.domain.user.dto.UserRequest;
import com.tasklean.api.domain.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages user profiles — lookup by UID, listing, profile updates, and account
 * soft-deletion. Account creation lives in {@link com.tasklean.api.auth.AuthService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UploadService uploadService;
    private final MembershipClosure membershipClosure;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;

    /**
     * Returns a user by their public UID.
     *
     * @param uid the user's public UID
     * @return the user
     * @throws ResourceNotFoundException if no user has that UID
     */
    public UserResponse getUserByUid(String uid) {
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    /**
     * Returns all users.
     *
     * @return all users
     */
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    /**
     * Updates a user's name parts, and their photo when they are editing their own account. The
     * photo is an upload of ours addressed by uid, never a URL a caller sends.
     *
     * @param uid          the user's public UID
     * @param request      the new name parts, and the photo uid when editing your own account
     * @param callerUserId the id of the authenticated caller
     * @return the updated user
     * @throws ResourceNotFoundException if no active user has that UID
     * @throws AccessDeniedException     if a caller other than the subject sent a photo uid
     */
    @Transactional
    public UserResponse updateUser(String uid, UserRequest request, Long callerUserId) {
        // Filtered: a closed account is no longer an editable target, for its own holder on a
        // still-valid access token as much as for an admin.
        User user = userRepository.findByUidAndIsActiveTrue(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));
        boolean editingSelf = callerUserId.equals(user.getIdUser());
        // A picture is self-service, SUPER_ADMIN included: a photo uid from anyone but the subject
        // is refused rather than applied, since nobody puts a face on somebody else's profile.
        if (!editingSelf && request.getPhotoUid() != null) {
            throw new AccessDeniedException(ErrorMessages.ACCESS_DENIED);
        }
        // Resolved before the tracked sets so the old uid is still readable as the previous value.
        // An administrative edit re-sets what is already there, so it cannot clear a photo by
        // leaving the field out - which the subject's own edit is still free to do.
        String photoUid = editingSelf
                ? uploadService.attachUserPhoto(request.getPhotoUid(), user, callerUserId)
                : user.getPhotoUid();
        AuditChanges changes = AuditChanges.create()
                .set("name", user::getName, user::setName, request.getName())
                .set("middleName", user::getMiddleName, user::setMiddleName, request.getMiddleName())
                .set("lastName", user::getLastName, user::setLastName, request.getLastName())
                .set("photoUid", user::getPhotoUid, user::setPhotoUid, photoUid);
        User saved = userRepository.save(user);
        auditLogService.recordEvent(AuditEntityType.USER, saved.getIdUser(), AuditAction.UPDATE, "Profile updated",
                null, changes);
        return UserResponse.from(saved);
    }

    /**
     * Changes a user's platform role.
     *
     * @param uid  the user's public UID
     * @param role the new platform role
     * @return the updated user
     * @throws ResourceNotFoundException if no user has that UID
     */
    @Transactional
    public UserResponse updateUserRole(String uid, UserRole role) {
        // Writes target a live account only, the same split the membership service keeps: reads
        // stay permissive so staff can still see a closed account, but granting it a platform role
        // would hand privilege to something nobody can sign in to.
        User user = userRepository.findByUidAndIsActiveTrue(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));

        AuditChanges changes = AuditChanges.create()
                .setVisible("role", user::getRole, user::setRole, role);
        User saved = userRepository.save(user);

        // Authorization-relevant state change → INFO.
        log.info("User platform role changed: uid={} role={}", uid, role);
        auditLogService.recordEvent(AuditEntityType.USER, saved.getIdUser(), AuditAction.ROLE_CHANGED,
                "Platform role changed to " + role, null, changes);

        return UserResponse.from(saved);
    }

    /**
     * Soft-deletes a user account (sets it inactive), releases its photo, ends its group
     * memberships and revokes every session it holds. The platform-admin counterpart to
     * {@code POST /api/account/deactivate}, and it has to close the same things: an account left
     * with live refresh tokens is only deleted until the next refresh, and one left in its
     * households still reads as a current member of them.
     *
     * @param uid          the user's public UID
     * @param callerUserId the id of the authenticated caller, attributed as having ended the
     *                     memberships
     * @throws ResourceNotFoundException if no active user has that UID
     */
    @Transactional
    public void deleteUser(String uid, Long callerUserId) {
        // Filtered, so deleting twice is a 404 rather than a second entry on the account's trail.
        User user = userRepository.findByUidAndIsActiveTrue(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));
        user.setIsActive(false);
        // The photo goes with the account: left attached, nothing would ever collect the object.
        uploadService.releaseUserPhoto(user);
        user.setPhotoUid(null);
        userRepository.save(user);
        // After the account is inactive, so a household left without an admin inherits one from
        // its surviving members rather than from the person on their way out.
        membershipClosure.closeFor(user, callerUserId);
        // The JWT filter is stateless, so the access token lives out its TTL either way - but
        // refresh is where a session is renewed, and that has to stop here.
        refreshTokenService.revokeAllUserTokens(user);
        log.info("User soft-deleted: uid={}", uid);
        auditLogService.recordEvent(AuditEntityType.USER, user.getIdUser(), AuditAction.ACCOUNT_DELETED, "Account deleted", null);
    }
}
