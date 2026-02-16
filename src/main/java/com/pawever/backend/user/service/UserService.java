package com.pawever.backend.user.service;

import com.pawever.backend.global.common.StorageService;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.user.dto.UserProfileResponse;
import com.pawever.backend.user.dto.UserUpdateRequest;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final StorageService storageService;

    public UserProfileResponse getProfile(Long userId) {
        User user = findActiveUser(userId);
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UserUpdateRequest request) {
        User user = findActiveUser(userId);
        user.updateProfile(request.getName(), request.getNickname(), request.getPhone());
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse updateProfileImage(Long userId, MultipartFile file) {
        User user = findActiveUser(userId);

        if (user.getProfileImageUrl() != null) {
            storageService.delete(user.getProfileImageUrl());
        }
        String imageUrl = storageService.upload(file, "users/" + userId + "/profile");

        user.updateProfileImage(imageUrl);
        return UserProfileResponse.from(user);
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = findActiveUser(userId);
        if (user.isDeleted()) {
            throw new CustomException(ErrorCode.USER_ALREADY_DELETED);
        }
        user.withdraw();
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
