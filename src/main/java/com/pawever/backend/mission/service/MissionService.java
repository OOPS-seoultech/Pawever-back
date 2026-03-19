package com.pawever.backend.mission.service;

import com.pawever.backend.checklist.service.ChecklistService;
import com.pawever.backend.global.common.StorageService;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.mission.dto.*;
import com.pawever.backend.mission.entity.*;
import com.pawever.backend.mission.repository.*;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.repository.PetRepository;
import com.pawever.backend.pet.repository.UserPetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionService {

    private final MissionRepository missionRepository;
    private final PetMissionRepository petMissionRepository;
    private final PetRepository petRepository;
    private final UserPetRepository userPetRepository;
    private final StorageService storageService;
    private final ChecklistService checklistService;

    /**
     * 발자국 남기기 미션 목록 + 달성 현황 조회
     */
    public MissionProgressResponse getMissionProgress(Long userId, Long petId) {
        validatePetAccess(userId, petId);

        List<Mission> allMissions = missionRepository.findAllByOrderByOrderIndexAscIdAsc();
        List<PetMission> petMissions = petMissionRepository.findByPetId(petId);
        Map<Long, PetMission> petMissionMap = petMissions.stream()
                .collect(Collectors.toMap(pm -> pm.getMission().getId(), Function.identity()));

        List<MissionResponse> missionResponses = allMissions.stream()
                .map(mission -> MissionResponse.of(mission, petMissionMap.get(mission.getId())))
                .toList();

        long completed = petMissions.stream().filter(PetMission::getCompleted).count();

        return MissionProgressResponse.builder()
                .completed(completed)
                .total(allMissions.size())
                .missions(missionResponses)
                .build();
    }

    /**
     * 발자국 남기기 미션 완료 처리
     */
    @Transactional
    public MissionResponse completeMission(Long userId, Long petId, Long missionId, MultipartFile file) {
        validatePetAccess(userId, petId);

        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_FOUND));

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSION_NOT_FOUND));

        PetMission petMission = petMissionRepository.findByPetIdAndMissionId(petId, missionId)
                .orElseGet(() -> {
                    PetMission newPm = PetMission.builder()
                            .pet(pet)
                            .mission(mission)
                            .build();
                    return petMissionRepository.save(newPm);
                });

        if (file != null && !file.isEmpty()) {
            if (petMission.getImageUrl() != null) {
                storageService.delete(petMission.getImageUrl());
            }
            String imageUrl = storageService.upload(file, "pets/" + petId + "/missions/" + petMission.getId());
            petMission.complete(imageUrl);
        } else {
            petMission.complete();
        }

        return MissionResponse.of(mission, petMission);
    }

    /**
     * 홈화면 진행률 요약 조회 (체크리스트 %, 미션 완료/전체)
     */
    public HomeProgressResponse getHomeProgress(Long userId, Long petId) {
        validatePetAccess(userId, petId);

        double checklistProgressPercent = checklistService.getChecklistProgressPercent(userId, petId);
        long missionTotal = missionRepository.count();
        long missionCompleted = petMissionRepository.countByPetIdAndCompletedTrue(petId);

        return HomeProgressResponse.builder()
                .checklistProgressPercent(checklistProgressPercent)
                .missionCompleted(missionCompleted)
                .missionTotal(missionTotal)
                .build();
    }

    private void validatePetAccess(Long userId, Long petId) {
        if (!userPetRepository.existsByUserIdAndPetId(userId, petId)) {
            throw new CustomException(ErrorCode.PET_NOT_OWNED);
        }
    }
}
