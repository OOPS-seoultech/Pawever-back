package com.pawever.backend.mission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class HomeProgressResponse {

    private int farewellPreviewProgressPercent;
    private long missionCompleted;
    private long missionTotal;
}
