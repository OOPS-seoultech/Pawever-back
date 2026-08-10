package com.pawever.backend.goodssurvey.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubscribeGoodsSurveyNoticeRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // 광고성 정보 수신 동의 없이는 받지 않는다.
        @AssertTrue boolean noticeAgreed
) {
}
