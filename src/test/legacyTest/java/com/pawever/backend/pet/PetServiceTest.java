package com.pawever.backend.pet;

import com.pawever.backend.global.common.StorageService;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.pet.dto.*;
import com.pawever.backend.pet.entity.*;
import com.pawever.backend.pet.repository.*;
import com.pawever.backend.pet.service.PetService;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import com.pawever.backend.mission.repository.MissionRepository;
import com.pawever.backend.mission.repository.PetMissionRepository;
import com.pawever.backend.farewellpreview.repository.FarewellPreviewProgressRepository;
import com.pawever.backend.memorial.repository.EmergencyProgressRepository;
import com.pawever.backend.funeral.repository.PetFuneralCompanyRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PetServiceTest {

    @InjectMocks
    private PetService petService;

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

    // =========================
    // createPet
    // =========================
    @Test
    void createPet_success() {
        Long userId = 1L;

        User user = User.builder()
                .id(userId)
                .build();

        AnimalType animalType = AnimalType.builder()
                .id(1L)
                .name("DOG")
                .build();

        Breed breed = Breed.builder()
                .id(1L)
                .animalType(animalType)
                .build();

        PetCreateRequest request = new PetCreateRequest();
        ReflectionTestUtils.setField(request, "name", "강아지");
        ReflectionTestUtils.setField(request, "breedId", 1L);
        ReflectionTestUtils.setField(request, "birthDate", LocalDate.now());
        ReflectionTestUtils.setField(request, "lifecycleStatus", LifecycleStatus.BEFORE_FAREWELL);

        Pet savedPet = Pet.builder()
                .id(10L)
                .breed(breed)
                .name("강아지")
                .build();

        // mocking
        when(userRepository.findByIdAndDeletedAtIsNull(userId))
                .thenReturn(Optional.of(user));
        when(userPetRepository.existsByUserIdAndIsOwnerTrue(userId))
                .thenReturn(false);
        when(breedRepository.findById(1L))
                .thenReturn(Optional.of(breed));
        when(petRepository.save(any()))
                .thenReturn(savedPet);
        when(missionRepository.findAllByOrderByOrderIndexAscIdAsc())
                .thenReturn(List.of());

        // 실행
        PetResponse response = petService.createPet(userId, request);

        // 검증
        assertNotNull(response);
        verify(petRepository).save(any());
        verify(userPetRepository).save(any());
    }

    // =========================
    // getPet
    // =========================
    @Test
    void getPet_success() {
        Long userId = 1L;
        Long petId = 1L;

        User user = User.builder().id(userId).selectedPetId(petId).build();
        Pet pet = Pet.builder().id(petId).build();
        UserPet userPet = UserPet.builder().user(user).pet(pet).isOwner(true).build();

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(petRepository.findById(petId)).thenReturn(Optional.of(pet));
        when(userPetRepository.findByUserIdAndPetId(userId, petId)).thenReturn(Optional.of(userPet));

        PetResponse response = petService.getPet(userId, petId);

        assertEquals(petId, response.getId());
    }

    // =========================
    // updatePet - owner 아님
    // =========================
    @Test
    void updatePet_notOwner() {
        Long userId = 1L;
        Long petId = 1L;

        User user = User.builder().id(userId).build();
        Pet pet = Pet.builder().id(petId).build();
        UserPet userPet = UserPet.builder().user(user).pet(pet).isOwner(false).build();

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(petRepository.findById(petId)).thenReturn(Optional.of(pet));
        when(userPetRepository.findByUserIdAndPetId(userId, petId)).thenReturn(Optional.of(userPet));

        PetUpdateRequest request = new PetUpdateRequest();

        assertThrows(CustomException.class,
                () -> petService.updatePet(userId, petId, request));
    }

    // =========================
    // deletePet
    // =========================
    @Test
    void deletePet_success() {
        Long userId = 1L;
        Long petId = 1L;

        User user = User.builder().id(userId).selectedPetId(petId).build();
        Pet pet = Pet.builder().id(petId).build();
        UserPet userPet = UserPet.builder().user(user).pet(pet).isOwner(true).build();

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userPetRepository.findByUserIdAndPetId(userId, petId)).thenReturn(Optional.of(userPet));
        when(userPetRepository.findByPetId(petId)).thenReturn(List.of(userPet));

        petService.deletePet(userId, petId);

        verify(petMissionRepository).deleteByPetId(petId);
        verify(farewellPreviewProgressRepository).deleteByPetId(petId);
        verify(emergencyProgressRepository).deleteByPetId(petId);
        verify(petFuneralCompanyRepository).deleteByPetId(petId);
        verify(userPetRepository).deleteAll(any());
        verify(petRepository, never()).deleteById(any());
    }

    // =========================
    // switchPet
    // =========================
    @Test
    void switchPet_success() {
        Long userId = 1L;
        Long petId = 2L;

        User user = User.builder().id(userId).build();
        Pet pet = Pet.builder().id(petId).build();
        UserPet userPet = UserPet.builder().user(user).pet(pet).isOwner(true).build();

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userPetRepository.findByUserIdAndPetId(userId, petId)).thenReturn(Optional.of(userPet));

        PetResponse response = petService.switchPet(userId, petId);

        assertEquals(petId, response.getId());
    }

    // =========================
    // uploadPetImage
    // =========================
    @Test
    void uploadPetImage_success() {
        Long userId = 1L;
        Long petId = 1L;

        User user = User.builder().id(userId).build();
        Pet pet = Pet.builder().id(petId).build();
        UserPet userPet = UserPet.builder().user(user).pet(pet).isOwner(true).build();

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "dummy".getBytes()
        );

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(petRepository.findById(petId)).thenReturn(Optional.of(pet));
        when(userPetRepository.findByUserIdAndPetId(userId, petId)).thenReturn(Optional.of(userPet));
        when(storageService.upload(any(), any())).thenReturn("url");

        PetResponse response = petService.uploadPetImage(userId, petId, file);

        assertNotNull(response);
        verify(storageService).upload(any(), any());
    }

    // =========================
    // getSelectedPet - 없음
    // =========================
    @Test
    void getSelectedPet_notFound() {
        User user = User.builder().id(1L).selectedPetId(null).build();

        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        assertThrows(CustomException.class,
                () -> petService.getSelectedPet(1L));
    }
}