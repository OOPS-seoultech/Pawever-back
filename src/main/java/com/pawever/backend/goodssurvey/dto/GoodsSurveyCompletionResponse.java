package com.pawever.backend.goodssurvey.dto;

import java.time.Instant;

public record GoodsSurveyCompletionResponse(
        String responseId,
        String status,
        int remaining,
        Instant reservationExpiresAt
) {
}
