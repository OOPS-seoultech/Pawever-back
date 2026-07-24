package com.pawever.backend.goodssurvey.dto;

public record GoodsSurveyApplicationResponse(
        String responseId,
        Long applicationId,
        String status,
        int remaining
) {
}
