package com.pawever.backend.farewellpreview.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class FarewellPreviewProgressUpdateRequest {

    private Boolean hasCompletedGuide;
    private String currentStepId;
    private List<String> enteredStepIds;
    private Boolean farewellMethodConfirmed;
    private Integer restingActiveStepNumber;
    private Integer restingCompletedStepCount;
    private List<String> administrationCompletedItemIds;
    private List<String> belongingsSelectedOptionIds;
    private Boolean belongingsConfirmed;
    private List<String> supportCompletedItemIds;
    private Boolean supportConfirmed;
}
