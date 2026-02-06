package com.pawever.backend.pet.service;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.mission.entity.ChecklistItem;
import com.pawever.backend.mission.entity.Mission;
import com.pawever.backend.mission.entity.PetChecklist;
import com.pawever.backend.mission.entity.PetMission;
import com.pawever.backend.mission.repository.ChecklistItemRepository;
import com.pawever.backend.mission.repository.MissionRepository;
import com.pawever.backend.mission.repository.PetChecklistRepository;
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
    private final ChecklistItemRepository checklistItemRepository;
    private final PetChecklistRepository petChecklistRepository;

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
                .lifecycleStatus(request.getLifecycleStatus())
                .build();
        pet = petRepository.save(pet);

        // 기존 선택된 펫 해제
        userPetRepository.findByUserIdAndSelectedTrue(userId)
                .ifPresent(UserPet::deselect);

        UserPet userPet = UserPet.builder()
                .user(user)
                .pet(pet)
                .isOwner(true)
                .selected(true)
                .build();
        userPetRepository.save(userPet);

        // 미션 초기화 (이별 전인 경우)
        if (request.getLifecycleStatus() == LifecycleStatus.BEFORE_FAREWELL) {
            initializeMissions(pet);
            initializeChecklist(pet);
        }

        return PetResponse.of(pet, true, true);
    }

    public PetResponse getPet(Long userId, Long petId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_FOUND));

        UserPet userPet = userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        return PetResponse.of(pet, userPet.getSelected(), userPet.getIsOwner());
    }

    public List<PetListResponse> getMyPets(Long userId) {
        return userPetRepository.findByUserId(userId).stream()
                .map(userPet -> PetListResponse.builder()
                        .id(userPet.getPet().getId())
                        .name(userPet.getPet().getName())
                        .profileImageUrl(userPet.getPet().getProfileImageUrl())
                        .selected(userPet.getSelected())
                        .build())
                .toList();
    }

    public PetResponse getSelectedPet(Long userId) {
        UserPet userPet = userPetRepository.findByUserIdAndSelectedTrue(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_FOUND));

        return PetResponse.of(userPet.getPet(), true, userPet.getIsOwner());
    }

    @Transactional
    public PetResponse updatePet(Long userId, Long petId, PetUpdateRequest request) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_FOUND));

        UserPet userPet = userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        Breed breed = request.getBreedId() != null
                ? breedRepository.findById(request.getBreedId())
                        .orElseThrow(() -> new CustomException(ErrorCode.BREED_NOT_FOUND))
                : pet.getBreed();

        pet.update(request.getName(), request.getBirthDate(), request.getGender(), request.getWeight(), breed);

        return PetResponse.of(pet, userPet.getSelected(), userPet.getIsOwner());
    }

    @Transactional
    public void deletePet(Long userId, Long petId) {
        UserPet userPet = userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        if (!userPet.getIsOwner()) {
            throw new CustomException(ErrorCode.NOT_OWNER);
        }

        // 모든 연결된 UserPet 삭제
        List<UserPet> allUserPets = userPetRepository.findByPetId(petId);
        userPetRepository.deleteAll(allUserPets);

        petRepository.deleteById(petId);
    }

    @Transactional
    public PetResponse switchPet(Long userId, Long petId) {
        userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        // 기존 선택 해제
        userPetRepository.findByUserIdAndSelectedTrue(userId)
                .ifPresent(UserPet::deselect);

        // 새로운 펫 선택
        UserPet targetUserPet = userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_FOUND));
        targetUserPet.select();

        return PetResponse.of(targetUserPet.getPet(), true, targetUserPet.getIsOwner());
    }

    @Transactional
    public PetResponse uploadPetImage(Long userId, Long petId, MultipartFile file) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_FOUND));

        UserPet userPet = userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));

        // TODO: AWS S3 파일 업로드 구현
        // String imageUrl = s3Service.upload(file, "pet/" + petId);
        String imageUrl = "https://s3.ap-northeast-2.amazonaws.com/pawever-bucket/pet/" + petId + "/profile.jpg";

        pet.updateProfileImage(imageUrl);

        return PetResponse.of(pet, userPet.getSelected(), userPet.getIsOwner());
    }

    public List<AnimalType> getAnimalTypes() {
        return new java.util.ArrayList<>(List.of()); // AnimalTypeRepository is not injected; see controller
    }

    private void initializeMissions(Pet pet) {
        List<Mission> missions = missionRepository.findAll();
        for (Mission mission : missions) {
            PetMission petMission = PetMission.builder()
                    .pet(pet)
                    .mission(mission)
                    .completed(false)
                    .build();
            petMissionRepository.save(petMission);
        }
    }

    private void initializeChecklist(Pet pet) {
        List<ChecklistItem> items = checklistItemRepository.findAll();
        for (ChecklistItem item : items) {
            PetChecklist petChecklist = PetChecklist.builder()
                    .pet(pet)
                    .checklistItem(item)
                    .completed(false)
                    .build();
            petChecklistRepository.save(petChecklist);
        }
    }
}
