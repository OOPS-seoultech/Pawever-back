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
    private String name;
    private String description;
    private Boolean completed;
    private LocalDateTime completedAt;
    private String imageUrl;

    public static MissionResponse of(Mission mission, PetMission petMission) {
        return MissionResponse.builder()
                .missionId(mission.getId())
                .name(mission.getName())
                .description(mission.getDescription())
                .completed(petMission != null && petMission.getCompleted())
                .completedAt(petMission != null ? petMission.getCompletedAt() : null)
                .imageUrl(petMission != null ? petMission.getImageUrl() : null)
                .build();
    }
}
