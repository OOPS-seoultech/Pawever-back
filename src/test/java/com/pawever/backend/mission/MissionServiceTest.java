package com.pawever.backend.mission;

import com.pawever.backend.checklist.service.ChecklistService;
import com.pawever.backend.global.common.StorageService;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.mission.dto.HomeProgressResponse;
import com.pawever.backend.mission.dto.MissionProgressResponse;
import com.pawever.backend.mission.dto.MissionResponse;
import com.pawever.backend.mission.entity.Mission;
import com.pawever.backend.mission.entity.PetMission;
import com.pawever.backend.mission.repository.MissionRepository;
import com.pawever.backend.mission.repository.PetMissionRepository;
import com.pawever.backend.mission.service.MissionService;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.repository.PetRepository;
import com.pawever.backend.pet.repository.UserPetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.mock.web.MockMultipartFile;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @InjectMocks
    private MissionService missionService;

    @Mock private MissionRepository missionRepository;
    @Mock private PetMissionRepository petMissionRepository;
    @Mock private PetRepository petRepository;
    @Mock private UserPetRepository userPetRepository;
    @Mock private StorageService storageService;
    @Mock private ChecklistService checklistService;

    // =========================
    // 미션 진행률 조회
    // =========================
    @Test
    void getMissionProgress_success() {
        Long userId = 1L;
        Long petId = 1L;

        when(userPetRepository.existsByUserIdAndPetId(userId, petId)).thenReturn(true);

        Mission mission = Mission.builder()
                .id(1L)
                .build();

        Pet pet = Pet.builder().id(petId).build();

        PetMission petMission = PetMission.builder()
                .pet(pet)
                .mission(mission)
                .build();

        petMission.complete(); // 완료 처리

        when(missionRepository.findAllByOrderByOrderIndexAscIdAsc())
                .thenReturn(List.of(mission));

        when(petMissionRepository.findByPetId(petId))
                .thenReturn(List.of(petMission));

        MissionProgressResponse response = missionService.getMissionProgress(userId, petId);

        assertEquals(1, response.getCompleted());
        assertEquals(1, response.getTotal());
        assertEquals(1, response.getMissions().size());
    }

    // =========================
    // 미션 완료 (파일 없음)
    // =========================
    @Test
    void completeMission_withoutFile() {
        Long userId = 1L;
        Long petId = 1L;
        Long missionId = 1L;

        when(userPetRepository.existsByUserIdAndPetId(userId, petId)).thenReturn(true);

        Pet pet = Pet.builder().id(petId).build();
        Mission mission = Mission.builder().id(missionId).build();

        PetMission petMission = PetMission.builder()
                .pet(pet)
                .mission(mission)
                .build();

        when(petRepository.findById(petId)).thenReturn(Optional.of(pet));
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(petMissionRepository.findByPetIdAndMissionId(petId, missionId))
                .thenReturn(Optional.of(petMission));

        MissionResponse response = missionService.completeMission(userId, petId, missionId, null);

        assertTrue(response.getCompleted());
    }

    // =========================
    // 미션 완료 (파일 있음)
    // =========================
    @Test
    void completeMission_withFile() {
        Long userId = 1L;
        Long petId = 1L;
        Long missionId = 1L;

        when(userPetRepository.existsByUserIdAndPetId(userId, petId)).thenReturn(true);

        Pet pet = Pet.builder().id(petId).build();
        Mission mission = Mission.builder().id(missionId).build();

        PetMission petMission = PetMission.builder()
                .id(10L)
                .pet(pet)
                .mission(mission)
                .build();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "dummy".getBytes()
        );

        when(petRepository.findById(petId)).thenReturn(Optional.of(pet));
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(petMissionRepository.findByPetIdAndMissionId(petId, missionId))
                .thenReturn(Optional.of(petMission));

        when(storageService.upload(any(), any())).thenReturn("url");

        MissionResponse response = missionService.completeMission(userId, petId, missionId, file);

        assertTrue(response.getCompleted());
        verify(storageService).upload(any(), any());
    }

    // =========================
    // 홈 진행률 조회
    // =========================
    @Test
    void getHomeProgress_success() {
        Long userId = 1L;
        Long petId = 1L;

        when(userPetRepository.existsByUserIdAndPetId(userId, petId)).thenReturn(true);

        when(checklistService.getChecklistProgressPercent(userId, petId))
                .thenReturn(50.0);

        when(missionRepository.count()).thenReturn(10L);
        when(petMissionRepository.countByPetIdAndCompletedTrue(petId))
                .thenReturn(5L);

        HomeProgressResponse response = missionService.getHomeProgress(userId, petId);

        assertEquals(50.0, response.getChecklistProgressPercent());
        assertEquals(5, response.getMissionCompleted());
        assertEquals(10, response.getMissionTotal());
    }

    // =========================
    // 권한 없음
    // =========================
    @Test
    void getMissionProgress_noAccess() {
        when(userPetRepository.existsByUserIdAndPetId(1L, 1L)).thenReturn(false);

        assertThrows(CustomException.class,
                () -> missionService.getMissionProgress(1L, 1L));
    }
}