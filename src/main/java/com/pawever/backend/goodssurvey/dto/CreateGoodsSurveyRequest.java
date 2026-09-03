package com.pawever.backend.goodssurvey.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param channel 어느 통로로 들어왔는지. 비어 있으면 상시 온라인 판매로 본다.
 *                통로가 값과 정원을 정하므로 여기서 정해진 것이 끝까지 간다.
 */
public record CreateGoodsSurveyRequest(
        @NotBlank @Size(max = 50) String questionnaireVersion,
        @NotBlank @Size(max = 30) String selectedGoods,
        @NotNull JsonNode tracking,
        @Size(max = 20) String channel
) {
}
