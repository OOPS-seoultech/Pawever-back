package com.pawever.backend.memorial.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class EmergencyProgressResponse {

    private LifecycleStatus lifecycleStatus;
    private Boolean emergencyMode;
    private Integer restingActiveStepNumber;
    private Integer restingCompletedStepCount;
    private Integer restingTotalStepCount;
    // 미리 살펴보기 안치준비 2단계에서 체크한 준비물 번호 목록 (1~6)
    // 1: 담요/이불, 2: 물티슈/거즈, 3: 배변패드/깨끗한 천, 4: 아이스팩, 5: 목받침용수건, 6: 위생장갑
    private List<Integer> restingStep2CheckedItemNumbers;
    private Boolean funeralCompanyCompleted;
    private LocalDateTime updatedAt;
}
