package com.pawever.backend.mission.dto;

import com.pawever.backend.global.util.UrlUtils;
import com.pawever.backend.mission.entity.Mission;
import com.pawever.backend.mission.entity.PetMission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
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
    private String mediaUrl;
    private String mediaType;
    private String mediaFormat;
    private Long mediaSizeBytes;
    private Integer mediaDurationSec;
    private List<Double> waveform;

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
                .imageUrl(petMission != null ? UrlUtils.toHttpsUrl(petMission.getImageUrl()) : null)
                .mediaUrl(petMission != null ? UrlUtils.toHttpsUrl(petMission.getMediaUrl()) : null)
                .mediaType(petMission != null ? petMission.getMediaType() : null)
                .mediaFormat(petMission != null ? petMission.getMediaFormat() : null)
                .mediaSizeBytes(petMission != null ? petMission.getMediaSizeBytes() : null)
                .mediaDurationSec(petMission != null ? petMission.getMediaDurationSec() : null)
                .waveform(parseWaveform(petMission != null ? petMission.getMediaWaveform() : null))
                .build();
    }

    private static List<Double> parseWaveform(String mediaWaveform) {
        if (mediaWaveform == null || mediaWaveform.isBlank()) {
            return List.of();
        }

        List<Double> waveform = new ArrayList<>();

        for (String rawValue : mediaWaveform.split(",")) {
            String value = rawValue.trim();

            if (value.isEmpty()) {
                continue;
            }

            try {
                waveform.add(Double.parseDouble(value));
            } catch (NumberFormatException ignored) {
                // Ignore malformed waveform entries and return the valid subset.
            }
        }

        return waveform;
    }
}
