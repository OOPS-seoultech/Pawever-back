package com.pawever.backend.goodssurvey.dto;

public record GoodsSurveyApplicationResponse(
        String responseId,
        Long applicationId,
        String status,
        int remaining,
        /** 설문에 답하고 온 신청인지. 화면이 어떤 값을 보여줄지 가른다. */
        boolean surveyParticipant,
        /** 실제 청구할 금액. 결제 화면이 이 값을 그대로 쓴다. */
        int paymentAmountKrw,
        /** 고객에게 읽어 줄 주문번호. */
        String orderNumber,
        int listPriceKrw,
        int discountAmountKrw
) {
}
