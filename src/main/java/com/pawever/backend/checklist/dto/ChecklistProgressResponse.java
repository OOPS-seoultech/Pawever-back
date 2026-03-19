package com.pawever.backend.checklist.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ChecklistProgressResponse {
    private double progressPercent;
    private long completed;
    private long total;
    private List<ChecklistResponse> items;
}
