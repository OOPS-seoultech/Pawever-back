package com.pawever.backend.user.service;

import com.pawever.backend.global.common.StorageService;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.global.security.HmacHasher;
import com.pawever.backend.pet.repository.UserPetRepository;
import com.pawever.backend.pet.service.PetService;
import com.pawever.backend.user.dto.NicknameCheckResponse;
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
    private final UserPetRepository userPetRepository;
    private final PetService petService;
    private final StorageService storageService;
    private final HmacHasher hmacHasher;

    public UserProfileResponse getProfile(Long userId) {
        User user = findActiveUser(userId);
        return UserProfileResponse.from(user);
    }

    /**
     * 닉네임 중복 여부 확인. 본인 닉네임은 제외(프로필 수정 시 기존 닉네임 유지 가능).
     * @param nickname null/blank면 사용 불가로 간주하여 available = false
     */
    public NicknameCheckResponse checkNicknameAvailable(Long userId, String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return NicknameCheckResponse.builder().available(false).build();
        }
        boolean taken = userRepository.existsByNicknameAndDeletedAtIsNullAndIdNot(
                nickname.trim(), userId);
        return NicknameCheckResponse.builder().available(!taken).build();
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UserUpdateRequest request) {
        User user = findActiveUser(userId);

        String newPhone = request.getPhone();
        String newPhoneHash = (newPhone != null && !newPhone.isBlank())
                ? hmacHasher.hash(newPhone)
                : null;

        // 다른 사용자가 해당 전화번호를 사용 중인지 확인
        if (newPhoneHash != null
                && !newPhoneHash.equals(user.getPhoneHash())
                && userRepository.existsByPhoneHashAndDeletedAtIsNull(newPhoneHash)) {
            throw new CustomException(ErrorCode.DUPLICATE_PHONE);
        }

        user.updateProfile(request.getName(), request.getNickname(), newPhone, newPhoneHash);
        // 프로필 정보를 최초/재저장하면 온보딩 완료로 간주
        user.completeOnboarding();
        if (request.getReferralType() != null) {
            user.updateReferral(request.getReferralType(), request.getReferralMemo());
        }
        if (request.getNotificationEnabled() != null) {
            user.updateNotificationConsent(request.getNotificationEnabled());
        }
        if (request.getMarketingEnabled() != null) {
            user.updateMarketingConsent(request.getMarketingEnabled());
        }
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

        // 해당 유저가 owner인 반려동물 모두 삭제 (Pet + UserPet).
        // 공유하던 다른 유저의 selectedPetId는 갱신하지 않음 → 홈 접근 시 410 SELECTED_PET_DELETED로 안내 후 반려동물 전환 페이지 유도.
        var ownedPetIds = userPetRepository.findByUserId(userId).stream()
                .filter(up -> Boolean.TRUE.equals(up.getIsOwner()))
                .map(up -> up.getPet().getId())
                .toList();
        for (Long petId : ownedPetIds) {
            petService.deletePetCascade(petId);
        }

        user.withdraw();
        userRepository.save(user);
    }

    @Transactional
    public void updateFcmToken(Long userId, String fcmToken) {
        User user = findActiveUser(userId);
        user.updateFcmToken(fcmToken);
    }

    public String getFcmToken(Long userId) {
        return findActiveUser(userId).getFcmToken();
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
