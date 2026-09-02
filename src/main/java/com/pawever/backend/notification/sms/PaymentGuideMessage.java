package com.pawever.backend.notification.sms;

import com.pawever.backend.goodssurvey.event.GoodsOrderSubmittedEvent;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * 입금 안내 문자의 문장을 만든다.
 *
 * 받는 사람이 이 문자 하나로 입금을 마칠 수 있어야 한다. 그래서 계좌·금액·
 * 입금자명·기한 넷을 모두 담는다. 하나라도 빠지면 되묻는 연락이 오고, 그
 * 사이 결제 대기 시간이 지나간다.
 *
 * 입금자명을 따로 적는 이유는 대조 때문이다. 무통장 입금은 들어온 돈에
 * 주문번호가 붙어 오지 않는다. 이름이 어긋나면 어느 주문의 돈인지 사람이
 * 찾아야 하고, 찾는 동안 주문은 만료된다.
 *
 * 광고가 아니라 주문 처리 안내이므로 (광고) 표기와 수신거부 번호를 넣지
 * 않는다. 넣으면 오히려 거짓이 된다.
 */
public final class PaymentGuideMessage {

    /** 알리고 LMS 한도. 넘기면 통째로 거절한다. */
    static final int MAX_LENGTH = 2000;

    /** 자유 입력이 들어오는 칸. 이름이 길다고 계좌가 밀려나면 안 된다. */
    private static final int MAX_FIELD_LENGTH = 60;

    private static final NumberFormat WON = NumberFormat.getIntegerInstance(Locale.KOREA);

    public static final String TITLE = "[포에버] 굿즈 주문 입금 안내";

    private PaymentGuideMessage() {
    }

    public static String of(
            GoodsOrderSubmittedEvent event,
            SmsProperties.Bank bank,
            int paymentWindowMinutes
    ) {
        String name = field(event.guardianName());
        return clip(
                """
                [포에버] 굿즈 주문 입금 안내

                %s님, 주문이 접수되었습니다.
                아래 계좌로 입금해 주시면 제작을 시작합니다.

                주문번호 %s
                입금금액 %s원
                입금계좌 %s %s
                예금주 %s
                입금자명 %s

                %s 안에 입금이 확인되지 않으면 주문이 자동으로 취소되고 보내주신 사진도 함께 파기됩니다.

                입금자명이 다르면 확인이 늦어질 수 있습니다. 문의는 이 문자에 회신 대신 pawever01@gmail.com 으로 주세요."""
                        .formatted(
                                name,
                                field(event.orderNumber()),
                                WON.format(event.paymentAmountKrw()),
                                field(bank.getName()),
                                field(bank.getAccount()),
                                field(bank.getHolder()),
                                name,
                                humanWindow(paymentWindowMinutes)
                        ),
                MAX_LENGTH
        );
    }

    /**
     * 기다리는 시간을 사람이 읽는 말로 바꾼다.
     *
     * 설정은 분으로 들어온다. 그대로 적으면 "2880분 안에"가 되는데, 받는
     * 사람은 그게 언제까지인지 계산해야 한다. 계산하게 만드는 안내는 안내가
     * 아니다.
     */
    static String humanWindow(int minutes) {
        if (minutes % (60 * 24) == 0) {
            return (minutes / (60 * 24)) + "일";
        }
        if (minutes % 60 == 0) {
            return (minutes / 60) + "시간";
        }
        return minutes + "분";
    }

    private static String field(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String trimmed = value.strip();
        return trimmed.length() <= MAX_FIELD_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_FIELD_LENGTH);
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
