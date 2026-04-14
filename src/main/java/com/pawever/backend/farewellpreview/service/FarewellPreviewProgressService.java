package com.pawever.backend.farewellpreview.service;

import com.pawever.backend.farewellpreview.dto.FarewellPreviewProgressResponse;
import com.pawever.backend.farewellpreview.dto.FarewellPreviewProgressUpdateRequest;
import com.pawever.backend.farewellpreview.entity.FarewellPreviewProgress;
import com.pawever.backend.farewellpreview.repository.FarewellPreviewProgressRepository;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.pet.entity.LifecycleStatus;
import com.pawever.backend.pet.entity.Pet;
import com.pawever.backend.pet.entity.UserPet;
import com.pawever.backend.pet.repository.UserPetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FarewellPreviewProgressService {

    private static final int MIN_MAIN_STEP = 1;
    private static final int MAX_MAIN_STEP = 5;
    private static final int MAX_RESTING_SUB_STEP = 7;      // 1~5: 다음으로, 6: 6단계 열기, 7: 6단계 완료
    private static final int MAX_ADMINISTRATION_SUB_STEP = 5;
    private static final int MAX_BELONGINGS_OPTION = 4;
    private static final int MAX_SUPPORT_SUB_STEP = 5;      // 1~4: 토글 확인, 5: 최종 확인

    private static final int[] AFTER_FAREWELL_ADMINISTRATION_PROGRESS = {10, 20, 30, 40, 50};
    private static final int[] AFTER_FAREWELL_SUPPORT_PROGRESS = {74, 81, 88, 95};

    private final FarewellPreviewProgressRepository farewellPreviewProgressRepository;
    private final UserPetRepository userPetRepository;

    public FarewellPreviewProgressResponse getFarewellPreviewProgress(Long userId, Long petId) {
        UserPet userPet = getAccessibleUserPet(userId, petId);
        Pet pet = userPet.getPet();
        boolean isOwner = Boolean.TRUE.equals(userPet.getIsOwner());

        FarewellPreviewProgress savedProgress = farewellPreviewProgressRepository.findByPetId(petId)
                .orElse(null);
        NormalizedState normalizedState = normalize(
                savedProgress == null ? null : Snapshot.from(savedProgress),
                pet.getLifecycleStatus()
        );

        return toResponse(
                normalizedState,
                pet.getLifecycleStatus(),
                isOwner,
                savedProgress == null ? null : savedProgress.getUpdatedAt()
        );
    }

    @Transactional
    public FarewellPreviewProgressResponse updateFarewellPreviewProgress(
            Long userId,
            Long petId,
            FarewellPreviewProgressUpdateRequest request
    ) {
        UserPet userPet = getAccessibleUserPet(userId, petId);

        if (!Boolean.TRUE.equals(userPet.getIsOwner())) {
            throw new CustomException(ErrorCode.NOT_OWNER);
        }

        Pet pet = userPet.getPet();
        NormalizedState normalizedState = normalize(Snapshot.from(request), pet.getLifecycleStatus());
        FarewellPreviewProgress progress = farewellPreviewProgressRepository.findByPetId(petId)
                .orElseGet(() -> FarewellPreviewProgress.builder()
                        .pet(pet)
                        .hasCompletedGuide(false)
                        .currentStep(getDefaultCurrentStep(pet.getLifecycleStatus()))
                        .build());

        progress.update(
                normalizedState.hasCompletedGuide(),
                normalizedState.currentStep(),
                normalizedState.enteredSteps(),
                normalizedState.completedMainSteps(),
                normalizedState.restingCompletedSubStepNumbers(),
                normalizedState.administrationCompletedSubStepNumbers(),
                normalizedState.belongingsSelectedOptionNumbers(),
                normalizedState.supportCompletedSubStepNumbers()
        );

        FarewellPreviewProgress savedProgress = farewellPreviewProgressRepository.saveAndFlush(progress);

        return toResponse(
                normalizedState,
                pet.getLifecycleStatus(),
                true,
                savedProgress.getUpdatedAt()
        );
    }

    public int getProgressPercent(Long userId, Long petId) {
        UserPet userPet = getAccessibleUserPet(userId, petId);
        LifecycleStatus lifecycleStatus = userPet.getPet().getLifecycleStatus();
        FarewellPreviewProgress savedProgress = farewellPreviewProgressRepository.findByPetId(petId)
                .orElse(null);

        return computeProgressPercent(normalize(
                savedProgress == null ? null : Snapshot.from(savedProgress),
                lifecycleStatus
        ));
    }

    private UserPet getAccessibleUserPet(Long userId, Long petId) {
        return userPetRepository.findByUserIdAndPetId(userId, petId)
                .orElseThrow(() -> new CustomException(ErrorCode.PET_NOT_OWNED));
    }

    private FarewellPreviewProgressResponse toResponse(
            NormalizedState s,
            LifecycleStatus lifecycleStatus,
            boolean isOwnerWritable,
            LocalDateTime updatedAt
    ) {
        return FarewellPreviewProgressResponse.builder()
                .lifecycleStatus(lifecycleStatus)
                .progressPercent(computeProgressPercent(s))
                .hasCompletedGuide(s.hasCompletedGuide())
                .currentStep(s.currentStep())
                .enteredSteps(List.copyOf(s.enteredSteps()))
                .completedMainSteps(List.copyOf(s.completedMainSteps()))
                .restingCompletedSubStepNumbers(List.copyOf(s.restingCompletedSubStepNumbers()))
                .administrationCompletedSubStepNumbers(List.copyOf(s.administrationCompletedSubStepNumbers()))
                .belongingsSelectedOptionNumbers(List.copyOf(s.belongingsSelectedOptionNumbers()))
                .supportCompletedSubStepNumbers(List.copyOf(s.supportCompletedSubStepNumbers()))
                .ownerWritable(isOwnerWritable)
                .updatedAt(updatedAt)
                .build();
    }

    private NormalizedState normalize(Snapshot snapshot, LifecycleStatus lifecycleStatus) {
        if (snapshot == null && lifecycleStatus == LifecycleStatus.AFTER_FAREWELL) {
            return new NormalizedState(
                    lifecycleStatus,
                    false,
                    null,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>()
            );
        }

        int defaultCurrentStep = getDefaultCurrentStep(lifecycleStatus);
        boolean isAfterFarewell = lifecycleStatus == LifecycleStatus.AFTER_FAREWELL;
        Supplier<List<Integer>> defaultEnteredSteps = () -> new ArrayList<>(List.of(defaultCurrentStep));

        Integer currentStep = normalizeMainStep(snapshot == null ? null : snapshot.currentStep());
        List<Integer> enteredSteps = dedupeAndFilterInts(
                snapshot == null ? null : snapshot.enteredSteps(), MIN_MAIN_STEP, MAX_MAIN_STEP);
        List<Integer> completedMainSteps = dedupeAndFilterInts(
                snapshot == null ? null : snapshot.completedMainSteps(), MIN_MAIN_STEP, MAX_MAIN_STEP);
        List<Integer> restingCompletedSubStepNumbers = dedupeAndFilterInts(
                snapshot == null ? null : snapshot.restingCompletedSubStepNumbers(), 1, MAX_RESTING_SUB_STEP);
        List<Integer> administrationCompletedSubStepNumbers = dedupeAndFilterInts(
                snapshot == null ? null : snapshot.administrationCompletedSubStepNumbers(), 1, MAX_ADMINISTRATION_SUB_STEP);
        List<Integer> belongingsSelectedOptionNumbers = dedupeAndFilterInts(
                snapshot == null ? null : snapshot.belongingsSelectedOptionNumbers(), 1, MAX_BELONGINGS_OPTION);
        List<Integer> supportCompletedSubStepNumbers = dedupeAndFilterInts(
                snapshot == null ? null : snapshot.supportCompletedSubStepNumbers(), 1, MAX_SUPPORT_SUB_STEP);

        if (currentStep == null) {
            currentStep = defaultCurrentStep;
        }

        if (enteredSteps.isEmpty()) {
            enteredSteps = defaultEnteredSteps.get();
        }

        if (isAfterFarewell) {
            int resolvedStep = currentStep;
            currentStep = switch (resolvedStep) {
                case 1, 2 -> 3;
                default -> resolvedStep;
            };
            enteredSteps = enteredSteps.stream()
                    .filter(step -> step != 1 && step != 2)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            if (enteredSteps.isEmpty()) {
                enteredSteps = defaultEnteredSteps.get();
            }
            if (!enteredSteps.contains(currentStep)) {
                enteredSteps.add(currentStep);
            }
            completedMainSteps = completedMainSteps.stream()
                    .filter(step -> step != 1 && step != 2)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

            return new NormalizedState(
                    lifecycleStatus,
                    true,
                    currentStep,
                    enteredSteps,
                    completedMainSteps,
                    new ArrayList<>(),
                    administrationCompletedSubStepNumbers,
                    belongingsSelectedOptionNumbers,
                    supportCompletedSubStepNumbers
            );
        }

        if (!enteredSteps.contains(currentStep)) {
            enteredSteps.add(currentStep);
        }

        return new NormalizedState(
                lifecycleStatus,
                snapshot != null && Boolean.TRUE.equals(snapshot.hasCompletedGuide()),
                currentStep,
                enteredSteps,
                completedMainSteps,
                restingCompletedSubStepNumbers,
                administrationCompletedSubStepNumbers,
                belongingsSelectedOptionNumbers,
                supportCompletedSubStepNumbers
        );
    }

    private int computeProgressPercent(NormalizedState s) {
        if (s.lifecycleStatus() == LifecycleStatus.AFTER_FAREWELL) {
            int supportToggleCount = (int) s.supportCompletedSubStepNumbers().stream()
                    .filter(n -> n >= 1 && n <= 4)
                    .count();
            return computeAfterFarewellProgress(
                    s.administrationCompletedSubStepNumbers().size(),
                    s.completedMainSteps().contains(4),
                    supportToggleCount,
                    s.supportCompletedSubStepNumbers().contains(5)
            );
        }

        int total = 0;

        // 1. 이별 방법 선택 (20%)
        if (s.completedMainSteps().contains(1)) total += 20;

        // 2. 안치 준비 (3+3+3+3+3+3+2 = 20%)
        List<Integer> resting = s.restingCompletedSubStepNumbers();
        if (resting.contains(1)) total += 3;
        if (resting.contains(2)) total += 3;
        if (resting.contains(3)) total += 3;
        if (resting.contains(4)) total += 3;
        if (resting.contains(5)) total += 3;
        if (resting.contains(6)) total += 3;  // 6단계 페이지 열기
        if (resting.contains(7)) total += 2;  // 6단계 완료 클릭

        // 3. 행정처리 (5+7+6+7+5 = 30%)
        List<Integer> admin = s.administrationCompletedSubStepNumbers();
        if (admin.contains(1)) total += 5;
        if (admin.contains(2)) total += 7;
        if (admin.contains(3)) total += 6;
        if (admin.contains(4)) total += 7;
        if (admin.contains(5)) total += 5;

        // 4. 물건정리 (10%)
        if (s.completedMainSteps().contains(4)) total += 10;

        // 5. 지원사업 (5+5+5+3+2 = 20%)
        List<Integer> support = s.supportCompletedSubStepNumbers();
        if (support.contains(1)) total += 5;
        if (support.contains(2)) total += 5;
        if (support.contains(3)) total += 5;
        if (support.contains(4)) total += 3;
        if (support.contains(5)) total += 2;  // 최종 확인 완료

        return total;
    }

    static int computeAfterFarewellProgress(
            int administrationCompletedCount,
            boolean belongingsConfirmed,
            int supportToggleCount,
            boolean supportConfirmed
    ) {
        if (supportConfirmed) {
            return 100;
        }

        int afterFarewellSupportProgress = resolveProgressByCount(
                supportToggleCount,
                AFTER_FAREWELL_SUPPORT_PROGRESS
        );
        if (afterFarewellSupportProgress > 0) {
            return afterFarewellSupportProgress;
        }
        if (belongingsConfirmed) {
            return 67;
        }

        return resolveProgressByCount(
                administrationCompletedCount,
                AFTER_FAREWELL_ADMINISTRATION_PROGRESS
        );
    }

    static int resolveProgressByCount(int completedCount, int[] progressByCount) {
        if (completedCount <= 0) {
            return 0;
        }

        return progressByCount[Math.min(progressByCount.length, completedCount) - 1];
    }

    private Integer normalizeMainStep(Integer value) {
        if (value == null) return null;
        return (value >= MIN_MAIN_STEP && value <= MAX_MAIN_STEP) ? value : null;
    }

    private List<Integer> dedupeAndFilterInts(List<Integer> values, int min, int max) {
        if (values == null || values.isEmpty()) return new ArrayList<>();
        Set<Integer> deduped = new LinkedHashSet<>();
        for (Integer value : values) {
            if (value != null && value >= min && value <= max) {
                deduped.add(value);
            }
        }
        return new ArrayList<>(deduped);
    }

    private int getDefaultCurrentStep(LifecycleStatus lifecycleStatus) {
        return lifecycleStatus == LifecycleStatus.AFTER_FAREWELL ? 3 : 1;
    }

    private record Snapshot(
            Boolean hasCompletedGuide,
            Integer currentStep,
            List<Integer> enteredSteps,
            List<Integer> completedMainSteps,
            List<Integer> restingCompletedSubStepNumbers,
            List<Integer> administrationCompletedSubStepNumbers,
            List<Integer> belongingsSelectedOptionNumbers,
            List<Integer> supportCompletedSubStepNumbers
    ) {
        private static Snapshot from(FarewellPreviewProgress progress) {
            return new Snapshot(
                    progress.getHasCompletedGuide(),
                    progress.getCurrentStep(),
                    progress.getEnteredSteps(),
                    progress.getCompletedMainSteps(),
                    progress.getRestingCompletedSubStepNumbers(),
                    progress.getAdministrationCompletedSubStepNumbers(),
                    progress.getBelongingsSelectedOptionNumbers(),
                    progress.getSupportCompletedSubStepNumbers()
            );
        }

        private static Snapshot from(FarewellPreviewProgressUpdateRequest request) {
            return new Snapshot(
                    request.getHasCompletedGuide(),
                    request.getCurrentStep(),
                    request.getEnteredSteps(),
                    request.getCompletedMainSteps(),
                    request.getRestingCompletedSubStepNumbers(),
                    request.getAdministrationCompletedSubStepNumbers(),
                    request.getBelongingsSelectedOptionNumbers(),
                    request.getSupportCompletedSubStepNumbers()
            );
        }
    }

    private record NormalizedState(
            LifecycleStatus lifecycleStatus,
            boolean hasCompletedGuide,
            Integer currentStep,
            List<Integer> enteredSteps,
            List<Integer> completedMainSteps,
            List<Integer> restingCompletedSubStepNumbers,
            List<Integer> administrationCompletedSubStepNumbers,
            List<Integer> belongingsSelectedOptionNumbers,
            List<Integer> supportCompletedSubStepNumbers
    ) {
    }
}
