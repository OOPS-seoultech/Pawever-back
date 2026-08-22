package com.pawever.backend.goodssurvey.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 수신거부 요청.
 *
 * 이메일 주소가 아니라 서명된 값을 받는다. 주소를 받으면 남의 주소를 적어
 * 넣어 대신 해지시킬 수 있다.
 */
public record GoodsSurveyUnsubscribeRequest(@NotBlank String token) {
}
