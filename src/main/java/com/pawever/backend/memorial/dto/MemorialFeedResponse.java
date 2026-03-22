package com.pawever.backend.memorial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class MemorialFeedResponse {
    private LocalDateTime referenceTime;
    private List<MemorialResponse> recentMemorials;
    private List<MemorialResponse> pastMemorials;
    private MemorialCursorPageInfo recentPageInfo;
    private MemorialCursorPageInfo pastPageInfo;
}
