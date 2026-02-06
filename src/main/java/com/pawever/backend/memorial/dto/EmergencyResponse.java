package com.pawever.backend.memorial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class EmergencyResponse {
    private MemorialResponse memorial;
    private List<GuideResponse> guides;
}
