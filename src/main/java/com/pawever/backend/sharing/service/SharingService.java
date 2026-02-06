package com.pawever.backend.sharing.service;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.entity.UserPet;
import com.pawever.backend.pet.repository.PetRepository;
import com.pawever.backend.pet.repository.UserPetRepository;
import com.pawever.backend.sharing.dto.InviteCodeResponse;
import com.pawever.backend.sharing.dto.SharedMemberResponse;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SharingService {

    private final PetRepository petRepository;
    private final UserPetRepository userPetRepository;
    private final UserRepository userRepository;

    /**
     * 같이 기록하기 멤버 목록 조회
     */
    public List<SharedMemberResponse> getSharedMembers(Long userId, Long petId) {
        userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        return userPetRepository.findByPetId(petId).stream()
                .map(userPet -> SharedMemberResponse.of(userPet.getUser(), userPet.getIsOwner()))
                .toList();
    }

    /**
     * 초대코드 조회
     */
    public InviteCodeResponse getInviteCode(Long userId, Long petId) {
        UserPet userPet = userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        Pet pet = userPet.getPet();

        return InviteCodeResponse.builder()
                .inviteCode(pet.getInviteCode())
                .petId(pet.getId())
                .petName(pet.getName())
                .build();
    }

    /**
     * 초대코드 재발급 (owner만 가능)
     */
    @Transactional
    public InviteCodeResponse regenerateInviteCode(Long userId, Long petId) {
        UserPet userPet = userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        if (!userPet.getIsOwner()) {
            throw new CustomException(ErrorCode.NOT_OWNER);
        }

        Pet pet = userPet.getPet();
        pet.regenerateInviteCode();

        return InviteCodeResponse.builder()
                .inviteCode(pet.getInviteCode())
                .petId(pet.getId())
                .petName(pet.getName())
                .build();
    }

    /**
     * 초대코드로 다른 아이 등록하기
     */
    @Transactional
    public void joinByInviteCode(Long userId, String inviteCode) {
        Pet pet = petRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_CODE));

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (userPetRepository.existsByUserIdAndPetId(userId, pet.getId())) {
            throw new CustomException(ErrorCode.ALREADY_SHARED);
        }

        // 기존 선택된 펫 해제
        userPetRepository.findByUserIdAndSelectedTrue(userId)
                .ifPresent(UserPet::deselect);

        UserPet userPet = UserPet.builder()
                .user(user)
                .pet(pet)
                .isOwner(false)
                .selected(true)
                .build();
        userPetRepository.save(userPet);
    }

    /**
     * 공유 끊기 (owner만 다른 멤버 공유 해제 가능)
     */
    @Transactional
    public void removeMember(Long userId, Long petId, Long targetUserId) {
        UserPet ownerUserPet = userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        if (!ownerUserPet.getIsOwner()) {
            throw new CustomException(ErrorCode.NOT_OWNER);
        }

        if (userId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.CANNOT_REMOVE_OWNER);
        }

        UserPet targetUserPet = userPetRepository.findByUserIdAndPetId(targetUserId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        userPetRepository.delete(targetUserPet);
    }
}
