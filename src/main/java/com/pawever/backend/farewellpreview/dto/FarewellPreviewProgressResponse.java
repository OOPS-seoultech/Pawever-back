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
    private String currentStepId;
    private List<String> enteredStepIds;
    private List<String> completedStepIds;
    private boolean farewellMethodConfirmed;
    private int restingActiveStepNumber;
    private int restingCompletedStepCount;
    private List<String> administrationCompletedItemIds;
    private List<String> belongingsSelectedOptionIds;
    private boolean belongingsConfirmed;
    private List<String> supportCompletedItemIds;
    private boolean supportConfirmed;

    @JsonProperty("isOwnerWritable")
    private boolean ownerWritable;

    private LocalDateTime updatedAt;
}
