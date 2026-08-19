package com.pawever.backend.pet.service;

import com.pawever.backend.farewellpreview.repository.FarewellPreviewProgressRepository;
import com.pawever.backend.funeral.repository.PetFuneralCompanyRepository;
import com.pawever.backend.global.common.StorageService;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.memorial.repository.EmergencyProgressRepository;
import com.pawever.backend.mission.entity.PetMission;
import com.pawever.backend.mission.repository.MissionRepository;
import com.pawever.backend.mission.repository.PetMissionRepository;
import com.pawever.backend.pet.dto.PetCreateRequest;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.entity.UserPet;
import com.pawever.backend.pet.repository.BreedRepository;
import com.pawever.backend.pet.repository.PetExpiredInviteCodeRepository;
import com.pawever.backend.pet.repository.PetRepository;
import com.pawever.backend.pet.repository.UserPetRepository;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 펫 삭제/유저 탈퇴 시 펫 프로필 이미지가 오브젝트 스토리지에서 실제로 파기되는지 검증 (개인정보 잔존 방지).
 */
@ExtendWith(MockitoExtension.class)
class PetServiceTest {

    @Mock private PetRepository petRepository;
    @Mock private UserPetRepository userPetRepository;
    @Mock private BreedRepository breedRepository;
    @Mock private UserRepository userRepository;
    @Mock private MissionRepository missionRepository;
    @Mock private PetMissionRepository petMissionRepository;
    @Mock private FarewellPreviewProgressRepository farewellPreviewProgressRepository;
    @Mock private EmergencyProgressRepository emergencyProgressRepository;
    @Mock private PetFuneralCompanyRepository petFuneralCompanyRepository;
    @Mock private PetExpiredInviteCodeRepository petExpiredInviteCodeRepository;
    @Mock private StorageService storageService;
    @Mock private SelectedPetResetter selectedPetResetter;

    @InjectMocks
    private PetService petService;

    @Test
    void deletePet_deletesPetProfileImageFromStorage() {
        User user = User.builder().id(1L).build();
        Pet pet = Pet.builder().id(10L).name("멍이")
                .profileImageUrl("https://cdn.example.com/pets/10/profile/x.jpg").build();
        UserPet ownerUp = UserPet.builder().user(user).pet(pet).isOwner(true).build();

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(userPetRepository.findByUserIdAndPetId(1L, 10L)).thenReturn(Optional.of(ownerUp));
        when(userPetRepository.findByPetId(10L)).thenReturn(List.of(ownerUp));

        petService.deletePet(1L, 10L);

        verify(storageService).delete("https://cdn.example.com/pets/10/profile/x.jpg");
    }

    @Test
    void deletePetCascade_deletesPetProfileImage_andClearsReference() {
        Pet pet = Pet.builder().id(10L).name("멍이")
                .profileImageUrl("https://cdn.example.com/pets/10/profile/y.jpg").build();
        UserPet ownerUp = UserPet.builder().user(User.builder().id(1L).build()).pet(pet).isOwner(true).build();

        when(petRepository.findById(10L)).thenReturn(Optional.of(pet));
        when(userPetRepository.findByPetId(10L)).thenReturn(List.of(ownerUp));

        petService.deletePetCascade(10L);

        verify(storageService).delete("https://cdn.example.com/pets/10/profile/y.jpg");
        assertNull(pet.getProfileImageUrl()); // 참조도 제거되어 dangling URL 없음
    }

    @Test
    void deletePetCascade_deletesMissionRecordingAndPhotoFromStorage() {
        // 미션 행만 지우면 목소리가 담긴 녹음 파일이 스토리지에 그대로 남는다.
        // 행이 사라지면 누구 것이었는지 찾을 수 없어 여기가 마지막 기회다.
        Pet pet = Pet.builder().id(10L).name("멍이").build();
        UserPet ownerUp = UserPet.builder().user(User.builder().id(1L).build()).pet(pet).isOwner(true).build();
        PetMission petMission = PetMission.builder()
                .pet(pet)
                .imageUrl("https://cdn.example.com/pets/10/missions/1/photo.jpg")
                .mediaUrl("https://cdn.example.com/pets/10/missions/1/recordings/voice.m4a")
                .build();

        when(petRepository.findById(10L)).thenReturn(Optional.of(pet));
        when(userPetRepository.findByPetId(10L)).thenReturn(List.of(ownerUp));
        when(petMissionRepository.findByPetId(10L)).thenReturn(List.of(petMission));

        petService.deletePetCascade(10L);

        verify(storageService).delete("https://cdn.example.com/pets/10/missions/1/recordings/voice.m4a");
        verify(storageService).delete("https://cdn.example.com/pets/10/missions/1/photo.jpg");
        verify(petMissionRepository).deleteByPetId(10L);
    }

    @Test
    void getSelectedPet_whenSelectedPetUnlinked_clearsViaResetterAndThrows() {
        User user = User.builder().id(1L).selectedPetId(10L).build();
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(userPetRepository.findByUserIdAndPetId(1L, 10L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> petService.getSelectedPet(1L));

        assertEquals(ErrorCode.SELECTED_PET_DELETED, ex.getErrorCode());
        verify(selectedPetResetter).clear(1L); // 별도 트랜잭션으로 selectedPetId 초기화가 호출됨
    }

    @Test
    void createPet_whenAlreadyOwnsPet_throwsOwnerLimit() {
        // 락 조회(findByIdAndDeletedAtIsNullForUpdate)로 유저를 얻은 뒤 소유 한도 검사
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(userPetRepository.existsByUserIdAndIsOwnerTrue(1L)).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class, () -> petService.createPet(1L, new PetCreateRequest()));

        assertEquals(ErrorCode.OWNER_PET_LIMIT_EXCEEDED, ex.getErrorCode());
    }
}
