package com.pawever.backend.farewellpreview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pawever.backend.pet.entity.LifecycleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FarewellPreviewProgressResponse {

    private LifecycleStatus lifecycleStatus;
    private int progressPercent;
    private boolean hasCompletedGuide;
    private Integer currentStep;
    private List<Integer> enteredSteps;
    private List<Integer> completedMainSteps;
    private List<Integer> restingCompletedSubStepNumbers;
    private List<Integer> restingStep2CheckedItemNumbers;
    private List<Integer> administrationCompletedSubStepNumbers;
    private List<Integer> belongingsSelectedOptionNumbers;
    private List<Integer> supportCompletedSubStepNumbers;

    @JsonProperty("isOwnerWritable")
    private boolean ownerWritable;

    private LocalDateTime updatedAt;
}
