package com.pawever.backend.memorial.dto;

import com.pawever.backend.memorial.entity.ReportReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ReportReasonResponse {
    private Long id;
    private String name;

    public static ReportReasonResponse from(ReportReason reason) {
        return ReportReasonResponse.builder()
                .id(reason.getId())
                .name(reason.getName())
                .build();
    }
}
