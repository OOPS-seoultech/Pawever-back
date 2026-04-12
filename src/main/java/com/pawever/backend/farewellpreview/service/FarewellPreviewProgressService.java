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

    private static final int MIN_STEP = 1;
    private static final int MAX_STEP = 5;
    private static final List<String> ADMINISTRATION_ITEM_IDS = List.of(
            "registryNumber",
            "reportOffice",
            "documents",
            "submitReport",
            "verifyReport"
    );
    private static final List<String> BELONGINGS_OPTION_IDS = List.of(
            "keep",
            "donate",
            "dispose",
            "memorialSpace"
    );
    private static final List<String> SUPPORT_ITEM_IDS = List.of(
            "seoulYouthMind",
            "youthMind",
            "nationalMind",
            "seoulMentalCenter"
    );
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
                        .farewellMethodConfirmed(false)
                        .restingActiveStepNumber(0)
                        .restingCompletedStepCount(0)
                        .belongingsConfirmed(false)
                        .supportConfirmed(false)
                        .build());

        progress.update(
                normalizedState.hasCompletedGuide(),
                normalizedState.currentStep(),
                normalizedState.enteredSteps(),
                normalizedState.farewellMethodConfirmed(),
                normalizedState.restingActiveStepNumber(),
                normalizedState.restingCompletedStepCount(),
                normalizedState.administrationCompletedItemIds(),
                normalizedState.belongingsSelectedOptionIds(),
                normalizedState.belongingsConfirmed(),
                normalizedState.supportCompletedItemIds(),
                normalizedState.supportConfirmed()
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
            NormalizedState normalizedState,
            LifecycleStatus lifecycleStatus,
            boolean isOwnerWritable,
            LocalDateTime updatedAt
    ) {
        List<Integer> completedSteps = buildCompletedSteps(normalizedState);

        return FarewellPreviewProgressResponse.builder()
                .lifecycleStatus(lifecycleStatus)
                .progressPercent(computeProgressPercent(normalizedState))
                .hasCompletedGuide(normalizedState.hasCompletedGuide())
                .currentStep(normalizedState.currentStep())
                .enteredSteps(List.copyOf(normalizedState.enteredSteps()))
                .completedSteps(completedSteps)
                .farewellMethodConfirmed(normalizedState.farewellMethodConfirmed())
                .restingActiveStepNumber(normalizedState.restingActiveStepNumber())
                .restingCompletedStepCount(normalizedState.restingCompletedStepCount())
                .administrationCompletedItemIds(List.copyOf(normalizedState.administrationCompletedItemIds()))
                .belongingsSelectedOptionIds(List.copyOf(normalizedState.belongingsSelectedOptionIds()))
                .belongingsConfirmed(normalizedState.belongingsConfirmed())
                .supportCompletedItemIds(List.copyOf(normalizedState.supportCompletedItemIds()))
                .supportConfirmed(normalizedState.supportConfirmed())
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
                    false,
                    0,
                    0,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    false,
                    new ArrayList<>(),
                    false
            );
        }

        int defaultCurrentStep = getDefaultCurrentStep(lifecycleStatus);
        boolean isAfterFarewell = lifecycleStatus == LifecycleStatus.AFTER_FAREWELL;
        Supplier<List<Integer>> defaultEnteredSteps = () -> new ArrayList<>(List.of(defaultCurrentStep));

        Integer currentStep = normalizeStep(snapshot == null ? null : snapshot.currentStep());
        List<Integer> enteredSteps = dedupeAndFilterSteps(snapshot == null ? null : snapshot.enteredSteps());
        List<String> administrationCompletedItemIds = dedupeAndFilter(
                snapshot == null ? null : snapshot.administrationCompletedItemIds(),
                ADMINISTRATION_ITEM_IDS
        );
        List<String> belongingsSelectedOptionIds = dedupeAndFilter(
                snapshot == null ? null : snapshot.belongingsSelectedOptionIds(),
                BELONGINGS_OPTION_IDS
        );
        List<String> supportCompletedItemIds = dedupeAndFilter(
                snapshot == null ? null : snapshot.supportCompletedItemIds(),
                SUPPORT_ITEM_IDS
        );

        int restingCompletedStepCount = clampRestingStepNumber(
                snapshot == null ? null : snapshot.restingCompletedStepCount(),
                0
        );
        int restingActiveStepNumber = clampRestingStepNumber(
                snapshot == null ? null : snapshot.restingActiveStepNumber(),
                restingCompletedStepCount == 0 ? 0 : Math.min(6, restingCompletedStepCount + 1)
        );

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

            return new NormalizedState(
                    lifecycleStatus,
                    true,
                    currentStep,
                    enteredSteps,
                    false,
                    0,
                    0,
                    administrationCompletedItemIds,
                    belongingsSelectedOptionIds,
                    snapshot != null && Boolean.TRUE.equals(snapshot.belongingsConfirmed()),
                    supportCompletedItemIds,
                    snapshot != null && Boolean.TRUE.equals(snapshot.supportConfirmed())
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
                snapshot != null && Boolean.TRUE.equals(snapshot.farewellMethodConfirmed()),
                restingActiveStepNumber,
                restingCompletedStepCount,
                administrationCompletedItemIds,
                belongingsSelectedOptionIds,
                snapshot != null && Boolean.TRUE.equals(snapshot.belongingsConfirmed()),
                supportCompletedItemIds,
                snapshot != null && Boolean.TRUE.equals(snapshot.supportConfirmed())
        );
    }

    private List<Integer> buildCompletedSteps(NormalizedState normalizedState) {
        List<Integer> completedSteps = new ArrayList<>();

        if (normalizedState.farewellMethodConfirmed()) completedSteps.add(1);
        if (normalizedState.restingCompletedStepCount() >= 6) completedSteps.add(2);
        if (normalizedState.administrationCompletedItemIds().size() == ADMINISTRATION_ITEM_IDS.size()) completedSteps.add(3);
        if (normalizedState.belongingsConfirmed()) completedSteps.add(4);
        if (normalizedState.supportConfirmed()) completedSteps.add(5);

        return completedSteps;
    }

    private int computeProgressPercent(NormalizedState normalizedState) {
        if (normalizedState.lifecycleStatus() == LifecycleStatus.AFTER_FAREWELL) {
            return computeAfterFarewellProgress(
                    normalizedState.administrationCompletedItemIds().size(),
                    normalizedState.belongingsConfirmed(),
                    normalizedState.supportCompletedItemIds().size(),
                    normalizedState.supportConfirmed()
            );
        }

        int total = 0;

        // Main 1: 이별 방법 선택 (20%)
        if (normalizedState.farewellMethodConfirmed()) total += 20;

        // Main 2: 안치 준비 (3+3+3+3+3+3+2 = 20%)
        int restingCompleted = normalizedState.restingCompletedStepCount();
        if (restingCompleted >= 1) total += 3;
        if (restingCompleted >= 2) total += 3;
        if (restingCompleted >= 3) total += 3;
        if (restingCompleted >= 4) total += 3;
        if (restingCompleted >= 5) total += 3;
        if (normalizedState.restingActiveStepNumber() >= 6) total += 3;
        if (restingCompleted >= 6) total += 2;

        // Main 3: 행정처리 (5+7+6+7+5 = 30%)
        List<String> adminIds = normalizedState.administrationCompletedItemIds();
        if (adminIds.contains("registryNumber")) total += 5;
        if (adminIds.contains("reportOffice"))   total += 7;
        if (adminIds.contains("documents"))      total += 6;
        if (adminIds.contains("submitReport"))   total += 7;
        if (adminIds.contains("verifyReport"))   total += 5;

        // Main 4: 물건정리 (10%)
        if (normalizedState.belongingsConfirmed()) total += 10;

        // Main 5: 지원사업 (5+5+5+3+2 = 20%)
        List<String> supportIds = normalizedState.supportCompletedItemIds();
        if (supportIds.contains("seoulYouthMind"))    total += 5;
        if (supportIds.contains("youthMind"))         total += 5;
        if (supportIds.contains("nationalMind"))      total += 5;
        if (supportIds.contains("seoulMentalCenter")) total += 3;
        if (normalizedState.supportConfirmed())       total += 2;

        return total;
    }

    static int computeAfterFarewellProgress(
            int administrationCompletedCount,
            boolean belongingsConfirmed,
            int supportCompletedCount,
            boolean supportConfirmed
    ) {
        if (supportConfirmed) {
            return 100;
        }

        int afterFarewellSupportProgress = resolveProgressByCount(
                supportCompletedCount,
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

    private Integer normalizeStep(Integer value) {
        if (value == null) {
            return null;
        }

        return (value >= MIN_STEP && value <= MAX_STEP) ? value : null;
    }

    private List<Integer> dedupeAndFilterSteps(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Integer> deduped = new LinkedHashSet<>();
        for (Integer value : values) {
            if (value != null && value >= MIN_STEP && value <= MAX_STEP) {
                deduped.add(value);
            }
        }

        return new ArrayList<>(deduped);
    }

    private List<String> dedupeAndFilter(List<String> values, List<String> allowedValues) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> deduped = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && allowedValues.contains(value)) {
                deduped.add(value);
            }
        }

        return new ArrayList<>(deduped);
    }

    private int clampRestingStepNumber(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }

        return Math.max(0, Math.min(6, value));
    }

    private int getDefaultCurrentStep(LifecycleStatus lifecycleStatus) {
        return lifecycleStatus == LifecycleStatus.AFTER_FAREWELL ? 3 : 1;
    }

    private record Snapshot(
            Boolean hasCompletedGuide,
            Integer currentStep,
            List<Integer> enteredSteps,
            Boolean farewellMethodConfirmed,
            Integer restingActiveStepNumber,
            Integer restingCompletedStepCount,
            List<String> administrationCompletedItemIds,
            List<String> belongingsSelectedOptionIds,
            Boolean belongingsConfirmed,
            List<String> supportCompletedItemIds,
            Boolean supportConfirmed
    ) {
        private static Snapshot from(FarewellPreviewProgress progress) {
            return new Snapshot(
                    progress.getHasCompletedGuide(),
                    progress.getCurrentStep(),
                    progress.getEnteredSteps(),
                    progress.getFarewellMethodConfirmed(),
                    progress.getRestingActiveStepNumber(),
                    progress.getRestingCompletedStepCount(),
                    progress.getAdministrationCompletedItemIds(),
                    progress.getBelongingsSelectedOptionIds(),
                    progress.getBelongingsConfirmed(),
                    progress.getSupportCompletedItemIds(),
                    progress.getSupportConfirmed()
            );
        }

        private static Snapshot from(FarewellPreviewProgressUpdateRequest request) {
            return new Snapshot(
                    request.getHasCompletedGuide(),
                    request.getCurrentStep(),
                    request.getEnteredSteps(),
                    request.getFarewellMethodConfirmed(),
                    request.getRestingActiveStepNumber(),
                    request.getRestingCompletedStepCount(),
                    request.getAdministrationCompletedItemIds(),
                    request.getBelongingsSelectedOptionIds(),
                    request.getBelongingsConfirmed(),
                    request.getSupportCompletedItemIds(),
                    request.getSupportConfirmed()
            );
        }
    }

    private record NormalizedState(
            LifecycleStatus lifecycleStatus,
            boolean hasCompletedGuide,
            Integer currentStep,
            List<Integer> enteredSteps,
            boolean farewellMethodConfirmed,
            int restingActiveStepNumber,
            int restingCompletedStepCount,
            List<String> administrationCompletedItemIds,
            List<String> belongingsSelectedOptionIds,
            boolean belongingsConfirmed,
            List<String> supportCompletedItemIds,
            boolean supportConfirmed
    ) {
    }
}
