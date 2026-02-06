package com.pawever.backend.memorial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class MemorialListResponse {
    private List<MemorialResponse> recentMemorials;    // 7일 이내
    private List<MemorialResponse> pastMemorials;      // 7일 초과
}
