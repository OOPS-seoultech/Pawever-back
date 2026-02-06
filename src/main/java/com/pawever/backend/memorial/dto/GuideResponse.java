package com.pawever.backend.memorial.dto;

import com.pawever.backend.memorial.entity.Guide;
import com.pawever.backend.memorial.entity.GuideStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class GuideResponse {
    private Long guideId;
    private String name;
    private List<StepResponse> steps;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class StepResponse {
        private Long stepId;
        private String title;
        private String description;
        private Integer orderIndex;

        public static StepResponse from(GuideStep step) {
            return StepResponse.builder()
                    .stepId(step.getId())
                    .title(step.getTitle())
                    .description(step.getDescription())
                    .orderIndex(step.getOrderIndex())
                    .build();
        }
    }

    public static GuideResponse from(Guide guide) {
        return GuideResponse.builder()
                .guideId(guide.getId())
                .name(guide.getName())
                .steps(guide.getSteps().stream()
                        .map(StepResponse::from)
                        .toList())
                .build();
    }
}
