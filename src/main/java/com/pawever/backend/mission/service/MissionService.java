package com.pawever.backend.mission.service;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.mission.dto.*;
import com.pawever.backend.mission.entity.*;
import com.pawever.backend.mission.repository.*;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.entity.UserPet;
import com.pawever.backend.pet.repository.PetRepository;
import com.pawever.backend.pet.repository.UserPetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ChecklistItemRepository checklistItemRepository;
    private final PetChecklistRepository petChecklistRepository;
    private final PetRepository petRepository;
    private final UserPetRepository userPetRepository;

    /**
     * 발자국 남기기 미션 목록 + 달성 현황 조회
     */
    public MissionProgressResponse getMissionProgress(Long userId, Long petId) {
        validatePetAccess(userId, petId);

        List<Mission> allMissions = missionRepository.findAll();
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
    public MissionResponse completeMission(Long userId, Long petId, Long missionId) {
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

        petMission.complete();

        return MissionResponse.of(mission, petMission);
    }

    /**
     * 이별준비 체크리스트 진행률 조회
     */
    public ChecklistProgressResponse getChecklistProgress(Long userId, Long petId) {
        validatePetAccess(userId, petId);

        List<ChecklistItem> allItems = checklistItemRepository.findAllByOrderByOrderIndexAsc();
        List<PetChecklist> petChecklists = petChecklistRepository.findByPetId(petId);
        Map<Long, PetChecklist> checklistMap = petChecklists.stream()
                .collect(Collectors.toMap(pc -> pc.getChecklistItem().getId(), Function.identity()));

        List<ChecklistResponse> responses = allItems.stream()
                .map(item -> ChecklistResponse.of(item, checklistMap.get(item.getId())))
                .toList();

        long completed = petChecklists.stream().filter(PetChecklist::getCompleted).count();
        long total = allItems.size();
        double progressPercent = total > 0 ? (double) completed / total * 100.0 : 0.0;

        return ChecklistProgressResponse.builder()
                .progressPercent(Math.round(progressPercent * 10.0) / 10.0)
                .completed(completed)
                .total(total)
                .items(responses)
                .build();
    }

    /**
     * 체크리스트 항목 토글
     */
    @Transactional
    public ChecklistResponse toggleChecklistItem(Long userId, Long petId, Long checklistItemId) {
        validatePetAccess(userId, petId);

        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_FOUND));

        ChecklistItem item = checklistItemRepository.findById(checklistItemId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHECKLIST_ITEM_NOT_FOUND));

        PetChecklist petChecklist = petChecklistRepository.findByPetIdAndChecklistItemId(petId, checklistItemId)
                .orElseGet(() -> {
                    PetChecklist newPc = PetChecklist.builder()
                            .pet(pet)
                            .checklistItem(item)
                            .build();
                    return petChecklistRepository.save(newPc);
                });

        if (petChecklist.getCompleted()) {
            petChecklist.uncheck();
        } else {
            petChecklist.complete();
        }

        return ChecklistResponse.of(item, petChecklist);
    }

    private void validatePetAccess(Long userId, Long petId) {
        if (!userPetRepository.existsByUserIdAndPetId(userId, petId)) {
            throw new CustomException(ErrorCode.PET_NOT_OWNED);
        }
    }
}
