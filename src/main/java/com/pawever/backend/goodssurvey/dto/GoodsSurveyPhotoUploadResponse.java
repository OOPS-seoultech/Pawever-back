package com.pawever.backend.goodssurvey.dto;

import java.time.Instant;
import java.util.Map;

public record GoodsSurveyPhotoUploadResponse(
        String photoId,
        String status,
        String uploadUrl,
        Map<String, String> headers,
        Instant expiresAt
) {
}
