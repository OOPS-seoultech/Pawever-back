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

    private static final List<String> STEP_IDS = List.of(
            "farewellMethod",
            "resting",
            "administration",
            "belongings",
            "support"
    );
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
    private static final int[] BEFORE_FAREWELL_SUPPORT_PROGRESS = {80, 85, 90, 95};
    private static final int[] BEFORE_FAREWELL_ADMINISTRATION_PROGRESS = {45, 52, 58, 65, 70};
    private static final int[] BEFORE_FAREWELL_RESTING_PROGRESS = {26, 29, 32, 35, 38, 40};

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
                        .currentStepId(getDefaultCurrentStepId(pet.getLifecycleStatus()))
                        .farewellMethodConfirmed(false)
                        .restingActiveStepNumber(0)
                        .restingCompletedStepCount(0)
                        .belongingsConfirmed(false)
                        .supportConfirmed(false)
                        .build());

        progress.update(
                normalizedState.hasCompletedGuide(),
                normalizedState.currentStepId(),
                normalizedState.enteredStepIds(),
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
        List<String> completedStepIds = buildCompletedStepIds(normalizedState);

        return FarewellPreviewProgressResponse.builder()
                .lifecycleStatus(lifecycleStatus)
                .progressPercent(computeProgressPercent(normalizedState))
                .hasCompletedGuide(normalizedState.hasCompletedGuide())
                .currentStepId(normalizedState.currentStepId())
                .enteredStepIds(List.copyOf(normalizedState.enteredStepIds()))
                .completedStepIds(completedStepIds)
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

        String defaultCurrentStepId = getDefaultCurrentStepId(lifecycleStatus);
        boolean isAfterFarewell = lifecycleStatus == LifecycleStatus.AFTER_FAREWELL;
        Supplier<List<String>> defaultEnteredStepIds = () -> new ArrayList<>(List.of(defaultCurrentStepId));

        String currentStepId = normalizeStepId(snapshot == null ? null : snapshot.currentStepId());
        List<String> enteredStepIds = dedupeAndFilter(snapshot == null ? null : snapshot.enteredStepIds(), STEP_IDS);
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

        if (currentStepId == null) {
            currentStepId = defaultCurrentStepId;
        }

        if (enteredStepIds.isEmpty()) {
            enteredStepIds = defaultEnteredStepIds.get();
        }

        if (isAfterFarewell) {
            currentStepId = switch (currentStepId) {
                case "farewellMethod", "resting" -> "administration";
                default -> currentStepId;
            };
            enteredStepIds = enteredStepIds.stream()
                    .filter(stepId -> !"farewellMethod".equals(stepId) && !"resting".equals(stepId))
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            if (enteredStepIds.isEmpty()) {
                enteredStepIds = defaultEnteredStepIds.get();
            }
            if (!enteredStepIds.contains(currentStepId)) {
                enteredStepIds.add(currentStepId);
            }

            return new NormalizedState(
                    lifecycleStatus,
                    true,
                    currentStepId,
                    enteredStepIds,
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

        if (!enteredStepIds.contains(currentStepId)) {
            enteredStepIds.add(currentStepId);
        }

        return new NormalizedState(
                lifecycleStatus,
                snapshot != null && Boolean.TRUE.equals(snapshot.hasCompletedGuide()),
                currentStepId,
                enteredStepIds,
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

    private List<String> buildCompletedStepIds(NormalizedState normalizedState) {
        List<String> completedStepIds = new ArrayList<>();

        if (normalizedState.farewellMethodConfirmed()) {
            completedStepIds.add("farewellMethod");
        }
        if (normalizedState.restingCompletedStepCount() >= 6) {
            completedStepIds.add("resting");
        }
        if (normalizedState.administrationCompletedItemIds().size() == ADMINISTRATION_ITEM_IDS.size()) {
            completedStepIds.add("administration");
        }
        if (normalizedState.belongingsConfirmed()) {
            completedStepIds.add("belongings");
        }
        if (normalizedState.supportConfirmed()) {
            completedStepIds.add("support");
        }

        return completedStepIds;
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

        if (normalizedState.supportConfirmed()) {
            return 100;
        }

        int beforeFarewellSupportProgress = resolveProgressByCount(
                normalizedState.supportCompletedItemIds().size(),
                BEFORE_FAREWELL_SUPPORT_PROGRESS
        );
        if (beforeFarewellSupportProgress > 0) {
            return beforeFarewellSupportProgress;
        }
        if (normalizedState.belongingsConfirmed()) {
            return 80;
        }

        int beforeFarewellAdministrationProgress = resolveProgressByCount(
                normalizedState.administrationCompletedItemIds().size(),
                BEFORE_FAREWELL_ADMINISTRATION_PROGRESS
        );
        if (beforeFarewellAdministrationProgress > 0) {
            return beforeFarewellAdministrationProgress;
        }

        int beforeFarewellRestingProgress = resolveProgressByCount(
                normalizedState.restingCompletedStepCount(),
                BEFORE_FAREWELL_RESTING_PROGRESS
        );
        if (beforeFarewellRestingProgress > 0) {
            return beforeFarewellRestingProgress;
        }
        if (normalizedState.farewellMethodConfirmed()) {
            return 20;
        }

        return 0;
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

    private String normalizeStepId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return STEP_IDS.contains(value) ? value : null;
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

    private String getDefaultCurrentStepId(LifecycleStatus lifecycleStatus) {
        return lifecycleStatus == LifecycleStatus.AFTER_FAREWELL ? "administration" : "farewellMethod";
    }

    private record Snapshot(
            Boolean hasCompletedGuide,
            String currentStepId,
            List<String> enteredStepIds,
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
                    progress.getCurrentStepId(),
                    progress.getEnteredStepIds(),
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
                    request.getCurrentStepId(),
                    request.getEnteredStepIds(),
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
            String currentStepId,
            List<String> enteredStepIds,
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
