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
    /** 미션 제목 (JSON: title) */
    private String title;
    /** 미션 부제/한줄 설명 (JSON: subtitle) */
    private String subtitle;
    /** 행동 가이드/상세 설명 (JSON: description 또는 guide) */
    private String description;
    private String illustrationPrompt;
    private Integer orderIndex;
    private Boolean completed;
    private LocalDateTime completedAt;
    private String imageUrl;

    public static MissionResponse of(Mission mission, PetMission petMission) {
        return MissionResponse.builder()
                .missionId(mission.getId())
                .category(mission.getCategory())
                .title(mission.getName())
                .subtitle(mission.getDescription())
                .description(mission.getActionGuide())
                .illustrationPrompt(mission.getIllustrationPrompt())
                .orderIndex(mission.getOrderIndex())
                .completed(petMission != null && petMission.getCompleted())
                .completedAt(petMission != null ? petMission.getCompletedAt() : null)
                .imageUrl(petMission != null ? petMission.getImageUrl() : null)
                .build();
    }
}
