package com.pawever.backend.goodssurvey.dto;

import jakarta.validation.constraints.NotBlank;

public record UnsubscribeGoodsSurveyNoticeRequest(
        @NotBlank String email
) {
}
