package com.pawever.backend.goodssurvey.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateGoodsSurveyRequest(
        @NotBlank @Size(max = 50) String questionnaireVersion,
        @NotBlank @Size(max = 30) String selectedGoods,
        @NotNull JsonNode tracking
) {
}
