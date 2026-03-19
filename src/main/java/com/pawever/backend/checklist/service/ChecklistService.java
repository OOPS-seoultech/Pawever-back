package com.pawever.backend.checklist.service;

import com.pawever.backend.checklist.dto.ChecklistProgressResponse;
import com.pawever.backend.checklist.dto.ChecklistResponse;
import com.pawever.backend.checklist.entity.ChecklistItem;
import com.pawever.backend.checklist.entity.PetChecklist;
import com.pawever.backend.checklist.repository.ChecklistItemRepository;
import com.pawever.backend.checklist.repository.PetChecklistRepository;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
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
public class ChecklistService {

    private final ChecklistItemRepository checklistItemRepository;
    private final PetChecklistRepository petChecklistRepository;
    private final PetRepository petRepository;
    private final UserPetRepository userPetRepository;

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
     * 체크리스트 진행률 퍼센트만 반환 (홈 진행률 등에서 사용)
     */
    public double getChecklistProgressPercent(Long userId, Long petId) {
        validatePetAccess(userId, petId);

        long checklistTotal = checklistItemRepository.count();
        long checklistCompleted = petChecklistRepository.countByPetIdAndCompletedTrue(petId);
        double progressPercent = checklistTotal > 0 ? (double) checklistCompleted / checklistTotal * 100.0 : 0.0;
        return Math.round(progressPercent * 10.0) / 10.0;
    }

    /**
     * 체크리스트 항목 토글
     */
    @Transactional
    public ChecklistResponse toggleChecklistItem(Long userId, Long petId, Long checklistItemId) {
        validatePetOwnerAccess(userId, petId);

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

    private void validatePetOwnerAccess(Long userId, Long petId) {
        UserPet userPet = userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));
        if (!Boolean.TRUE.equals(userPet.getIsOwner())) {
            throw new CustomException(ErrorCode.NOT_OWNER);
        }
    }
}
