package com.pawever.backend.memorial.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EmergencyProgressUpdateRequest {

    private Integer restingActiveStepNumber;
    private Integer restingCompletedStepCount;
    private Boolean funeralCompanyCompleted;
}
