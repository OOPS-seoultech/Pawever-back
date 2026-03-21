package com.pawever.backend.pet.service;

import com.pawever.backend.global.common.StorageService;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.mission.entity.Mission;
import com.pawever.backend.mission.entity.PetMission;
import com.pawever.backend.mission.repository.MissionRepository;
import com.pawever.backend.mission.repository.PetMissionRepository;
import com.pawever.backend.pet.dto.*;
import com.pawever.backend.pet.entity.*;
import com.pawever.backend.pet.repository.*;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PetService {

    private final PetRepository petRepository;
    private final UserPetRepository userPetRepository;
    private final BreedRepository breedRepository;
    private final UserRepository userRepository;
    private final MissionRepository missionRepository;
    private final PetMissionRepository petMissionRepository;
    private final StorageService storageService;

    private LocalDateTime toDeathDateTime(LocalDate deathDate) {
        return deathDate == null ? null : deathDate.atStartOfDay();
    }

    private LocalDateTime resolveCreateDeathDate(PetCreateRequest request) {
        if (request.getLifecycleStatus() == LifecycleStatus.BEFORE_FAREWELL) {
            if (request.getDeathDate() != null) {
                throw new CustomException(ErrorCode.INVALID_DEATH_DATE);
            }

            return null;
        }

        if (request.getDeathDate() == null) {
            throw new CustomException(ErrorCode.INVALID_DEATH_DATE);
        }

        return toDeathDateTime(request.getDeathDate());
    }

    private LocalDateTime resolveUpdatedDeathDate(Pet pet, PetUpdateRequest request) {
        if (request.getDeathDate() == null) {
            return pet.getDeathDate();
        }

        if (pet.getLifecycleStatus() != LifecycleStatus.AFTER_FAREWELL) {
            throw new CustomException(ErrorCode.INVALID_DEATH_DATE);
        }

        return toDeathDateTime(request.getDeathDate());
    }

    private Boolean resolveCreateNeutered(PetCreateRequest request) {
        return Boolean.TRUE.equals(request.getIsNeutered());
    }

    private Boolean resolveUpdatedNeutered(Pet pet, PetUpdateRequest request) {
        if (request.getIsNeutered() == null) {
            return pet.getIsNeutered();
        }

        return request.getIsNeutered();
    }

    @Transactional
    public PetResponse createPet(Long userId, PetCreateRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Breed breed = breedRepository.findById(request.getBreedId())
                .orElseThrow(() -> new CustomException(ErrorCode.BREED_NOT_FOUND));

        Pet pet = Pet.builder()
                .breed(breed)
                .name(request.getName())
                .birthDate(request.getBirthDate())
                .gender(request.getGender())
                .weight(request.getWeight())
                .isNeutered(resolveCreateNeutered(request))
                .deathDate(resolveCreateDeathDate(request))
                .lifecycleStatus(request.getLifecycleStatus())
                .build();

        if (request.getLifecycleStatus() == LifecycleStatus.AFTER_FAREWELL) {
            pet.activateEmergencyMode();
        }

        pet = petRepository.save(pet);

        UserPet userPet = UserPet.builder()
                .user(user)
                .pet(pet)
                .isOwner(true)
                .build();
        userPetRepository.save(userPet);

        // 새로 생성한 펫을 선택
        user.selectPet(pet.getId());

        // 미션 초기화 (이별 전인 경우)
        if (request.getLifecycleStatus() == LifecycleStatus.BEFORE_FAREWELL) {
            initializeMissions(pet);
        }

        return PetResponse.of(pet, user.getSelectedPetId(), true);
    }

    public PetResponse getPet(Long userId, Long petId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_FOUND));

        UserPet userPet = userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        return PetResponse.of(pet, user.getSelectedPetId(), userPet.getIsOwner());
    }

    public List<PetListResponse> getMyPets(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Long selectedPetId = user.getSelectedPetId();

        return userPetRepository.findByUserId(userId).stream()
                .map(userPet -> PetListResponse.builder()
                        .id(userPet.getPet().getId())
                        .name(userPet.getPet().getName())
                        .profileImageUrl(userPet.getPet().getProfileImageUrl())
                        .selected(userPet.getPet().getId().equals(selectedPetId))
                        .isOwner(userPet.getIsOwner())
                        .build())
                .toList();
    }

    public PetResponse getSelectedPet(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Long selectedPetId = user.getSelectedPetId();
        if (selectedPetId == null) {
            throw new CustomException(ErrorCode.PET_NOT_FOUND);
        }

        var userPetOpt = userPetRepository.findByUserIdAndPetId(userId, selectedPetId);
        if (userPetOpt.isEmpty()) {
            clearSelectedPetAndThrowDeleted(user);
        }
        UserPet userPet = userPetOpt.get();
        return PetResponse.of(userPet.getPet(), selectedPetId, userPet.getIsOwner());
    }

    /**
     * 선택된 반려동물이 이미 삭제된 경우(owner 탈퇴 등): selectedPetId 초기화 후 SELECTED_PET_DELETED 예외.
     * 클라이언트는 "이용 중이던 반려동물 프로필이 삭제되었습니다" 메시지 표시 후 선택 해제 상태로 전환.
     */
    @Transactional
    public PetResponse clearSelectedPetAndThrowDeleted(User user) {
        user.selectPet(null);
        userRepository.save(user);
        throw new CustomException(ErrorCode.SELECTED_PET_DELETED);
    }

    @Transactional
    public PetResponse updatePet(Long userId, Long petId, PetUpdateRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_FOUND));

        UserPet userPet = userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        if (!userPet.getIsOwner()) {
            throw new CustomException(ErrorCode.NOT_OWNER);
        }

        Breed breed = request.getBreedId() != null
                ? breedRepository.findById(request.getBreedId())
                        .orElseThrow(() -> new CustomException(ErrorCode.BREED_NOT_FOUND))
                : pet.getBreed();

        pet.update(
                request.getName(),
                request.getBirthDate(),
                request.getGender(),
                request.getWeight(),
                resolveUpdatedNeutered(pet, request),
                breed,
                resolveUpdatedDeathDate(pet, request)
        );

        return PetResponse.of(pet, user.getSelectedPetId(), userPet.getIsOwner());
    }

    @Transactional
    public void deletePet(Long userId, Long petId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        UserPet userPet = userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        if (!userPet.getIsOwner()) {
            throw new CustomException(ErrorCode.NOT_OWNER);
        }

        // 삭제하는 펫이 현재 선택된 펫이면 선택 해제
        if (petId.equals(user.getSelectedPetId())) {
            user.selectPet(null);
        }

        // 모든 연결된 UserPet 삭제
        List<UserPet> allUserPets = userPetRepository.findByPetId(petId);
        userPetRepository.deleteAll(allUserPets);

        petRepository.deleteById(petId);
    }

    /**
     * Pet과 연결된 모든 UserPet 삭제 후 Pet 삭제.
     * 유저 탈퇴 시 owner인 펫 정리용으로 사용 (권한/selectedPet 갱신 없음).
     */
    @Transactional
    public void deletePetCascade(Long petId) {
        List<UserPet> allUserPets = userPetRepository.findByPetId(petId);
        userPetRepository.deleteAll(allUserPets);
        petRepository.deleteById(petId);
    }

    @Transactional
    public PetResponse switchPet(Long userId, Long petId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        UserPet userPet = userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        user.selectPet(petId);

        return PetResponse.of(userPet.getPet(), user.getSelectedPetId(), userPet.getIsOwner());
    }

    @Transactional
    public PetResponse uploadPetImage(Long userId, Long petId, MultipartFile file) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_FOUND));

        UserPet userPet = userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        if (pet.getProfileImageUrl() != null) {
            storageService.delete(pet.getProfileImageUrl());
        }
        String imageUrl = storageService.upload(file, "pets/" + petId + "/profile");

        pet.updateProfileImage(imageUrl);

        return PetResponse.of(pet, user.getSelectedPetId(), userPet.getIsOwner());
    }

    public List<AnimalType> getAnimalTypes() {
        return new java.util.ArrayList<>(List.of()); // AnimalTypeRepository is not injected; see controller
    }

    private void initializeMissions(Pet pet) {
        List<Mission> missions = missionRepository.findAllByOrderByOrderIndexAscIdAsc();
        for (Mission mission : missions) {
            PetMission petMission = PetMission.builder()
                    .pet(pet)
                    .mission(mission)
                    .completed(false)
                    .build();
            petMissionRepository.save(petMission);
        }
    }
}
