package com.pawever.backend.goodssurvey.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record SaveGoodsSurveyDraftRequest(
        @NotNull @Size(max = 80) Map<String, JsonNode> answers,
        @Size(max = 30) String currentQuestionId,
        @PositiveOrZero Long surveyActiveMs,
        @NotNull @Size(max = 80) Map<String, Long> questionActiveMs,
        @NotNull JsonNode tracking
) {
}
