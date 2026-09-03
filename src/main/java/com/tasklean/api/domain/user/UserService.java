package com.tasklean.api.domain.user;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.auditlog.AuditAction;
import com.tasklean.api.domain.auditlog.AuditEntityType;
import com.tasklean.api.domain.auditlog.AuditLogService;
import com.tasklean.api.domain.user.dto.UserRequest;
import com.tasklean.api.domain.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * Updates a user's profile fields (name parts and photo).
     *
     * @param uid     the user's public UID
     * @param request the new profile details
     * @return the updated user
     * @throws ResourceNotFoundException if no user has that UID
     */
    @Transactional
    public UserResponse updateUser(String uid, UserRequest request) {
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));
        user.setName(request.getName());
        user.setMiddleName(request.getMiddleName());
        user.setLastName(request.getLastName());
        user.setPhotoUrl(request.getPhotoUrl());
        User saved = userRepository.save(user);
        auditLogService.recordEvent(AuditEntityType.USER, saved.getIdUser(), AuditAction.UPDATE, "Profile updated", null);
        return UserResponse.from(saved);
    }

    /**
     * Soft-deletes a user account (sets it inactive).
     *
     * @param uid the user's public UID
     * @throws ResourceNotFoundException if no user has that UID
     */
    @Transactional
    public void deleteUser(String uid) {
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));
        user.setIsActive(false);
        userRepository.save(user);
        log.info("User soft-deleted: uid={}", uid);
        auditLogService.recordEvent(AuditEntityType.USER, user.getIdUser(), AuditAction.ACCOUNT_DELETED, "Account deleted", null);
    }
}
