package com.tasklean.api.domain.user;

import com.tasklean.api.common.ErrorMessages;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.user.dto.UserRequest;
import com.tasklean.api.domain.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse getUserByUid(String uid) {
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse updateUser(String uid, UserRequest request) {
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));
        user.setName(request.getName());
        user.setMiddleName(request.getMiddleName());
        user.setLastName(request.getLastName());
        user.setPhotoUrl(request.getPhotoUrl());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(String uid) {
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));
        user.setIsActive(false);
        userRepository.save(user);
        log.info("User soft-deleted: uid={}", uid);
    }
}
