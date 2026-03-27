package com.pawever.backend.memorial.dto;

import com.pawever.backend.pet.entity.LifecycleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class EmergencyProgressResponse {

    private LifecycleStatus lifecycleStatus;
    private Boolean emergencyMode;
    private Integer restingActiveStepNumber;
    private Integer restingCompletedStepCount;
    private Integer restingTotalStepCount;
    private Boolean funeralCompanyCompleted;
    private LocalDateTime updatedAt;
}
