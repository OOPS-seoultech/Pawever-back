package com.pawever.backend.farewellpreview.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class FarewellPreviewProgressUpdateRequest {

    private Boolean hasCompletedGuide;
    private Integer currentStep;
    private List<Integer> enteredSteps;
    private List<Integer> completedMainSteps;
    private List<Integer> restingCompletedSubStepNumbers;
    private List<Integer> restingStep2CheckedItemNumbers;
    private List<Integer> administrationCompletedSubStepNumbers;
    private List<Integer> belongingsSelectedOptionNumbers;
    private List<Integer> supportCompletedSubStepNumbers;
}
