package com.pawever.backend.goodssurvey.dto;

public record GoodsSurveyDraftResponse(
        String responseId,
        String editToken,
        String status,
        int remaining
) {
}
