package com.pawever.backend.goodssurvey.dto;

import java.time.Instant;

/**
 * 신청이 접수된 뒤 화면이 받는 것.
 *
 * @param bank             어디로 얼마를 넣어야 하는지. 설정이 비어 있으면 null 이고,
 *                         그때 화면은 문자로만 안내한다.
 * @param paymentExpiresAt 이 시각까지 들어오지 않으면 자리가 돌아간다.
 */
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
        int discountAmountKrw,
        /** 배송비. 화면은 따로 보여 주고 청구는 위 금액에 합쳐져 있다. */
        int shippingFeeKrw,
        BankAccount bank,
        Instant paymentExpiresAt
) {
    /**
     * 입금받을 계좌.
     *
     * 문자에 적는 것과 같은 값이다. 화면과 문자가 다른 계좌를 말하면 사람은
     * 어느 쪽이 맞는지 알 수 없고, 잘못 넣은 돈은 대조도 되지 않는다.
     */
    public record BankAccount(String name, String account, String holder) {
    }
}
