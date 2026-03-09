package com.pawever.backend.mission.dto;

import com.pawever.backend.mission.entity.Mission;
import com.pawever.backend.mission.entity.PetMission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class MissionResponse {
    private Long missionId;
    private String category;
    private String name;
    private String description;
    private String actionGuide;
    private String illustrationPrompt;
    private Integer orderIndex;
    private Boolean completed;
    private LocalDateTime completedAt;
    private String imageUrl;

    public static MissionResponse of(Mission mission, PetMission petMission) {
        return MissionResponse.builder()
                .missionId(mission.getId())
                .category(mission.getCategory())
                .name(mission.getName())
                .description(mission.getDescription())
                .actionGuide(mission.getActionGuide())
                .illustrationPrompt(mission.getIllustrationPrompt())
                .orderIndex(mission.getOrderIndex())
                .completed(petMission != null && petMission.getCompleted())
                .completedAt(petMission != null ? petMission.getCompletedAt() : null)
                .imageUrl(petMission != null ? petMission.getImageUrl() : null)
                .build();
    }
}
