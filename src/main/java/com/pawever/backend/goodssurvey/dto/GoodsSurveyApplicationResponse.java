package com.pawever.backend.goodssurvey.dto;

public record GoodsSurveyApplicationResponse(
        String responseId,
        Long applicationId,
        String status,
        int remaining,
        /** 설문에 답하고 온 신청인지. 화면이 어떤 값을 보여줄지 가른다. */
        boolean surveyParticipant,
        /** 청구할 금액. 문자로 안내할 계좌 입금액과 같은 값이다. */
        int appliedPriceKrw
) {
}
