package com.pawever.backend.notification.telegram;

import com.pawever.backend.goodssurvey.event.GoodsOrderSubmittedEvent;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * 텔레그램에 보낼 문장을 만든다.
 *
 * parse_mode 를 HTML 로 쓰기 때문에, 우리가 적은 태그는 태그로 두고 사람이
 * 적은 값은 태그가 아닌 글자로 만들어야 한다. 이 구분이 없으면 신청자가
 * 적은 글자 하나에 알림 전체가 사라진다 — 텔레그램은 열리다 만 태그를 만나면
 * 400 을 돌려주고, 그 신청만 조용히 알림이 안 간다.
 *
 * 굿즈 직접 입력과 보호자 이름이 자유 입력이라 실제로 들어올 수 있는 값이다.
 * "키링 &lt;2개&gt;" 나 "A&amp;B" 로 충분히 걸린다.
 */
public final class TelegramMessage {

    /** 텔레그램이 한 번에 받는 글자 수. 넘기면 통째로 거절한다. */
    static final int MAX_LENGTH = 4096;

    /**
     * 값 하나가 차지할 수 있는 길이.
     *
     * 자유 입력이 있는 한 전체 길이는 우리가 정하는 값이 아니다. 값마다 먼저
     * 끊어 두면 한 칸이 길다고 다른 칸이 잘려 나가지 않는다.
     */
    private static final int MAX_FIELD_LENGTH = 200;

    private static final NumberFormat WON = NumberFormat.getIntegerInstance(Locale.KOREA);

    private TelegramMessage() {
    }

    public static String goodsOrderSubmitted(GoodsOrderSubmittedEvent event) {
        StringBuilder text = new StringBuilder()
                .append("🚨 <b>신규 굿즈 신청</b>\n")
                .append("- 이름: ").append(field(event.guardianName())).append('\n')
                .append("- 연락처: ").append(field(event.phone())).append('\n')
                .append("- 반려견: ").append(field(event.petName())).append('\n')
                .append("- 굿즈: ").append(field(event.goodsLabel())).append('\n')
                .append("- 결제: ").append(WON.format(event.paymentAmountKrw())).append('원');
        if (event.surveyParticipant()) {
            // 얼마를 받을지가 여기서 갈린다. 제작팀이 먼저 알아야 하는 값이다.
            text.append(" (설문 참여)");
        }
        text.append('\n')
                .append("- 유입경로: ").append(field(event.trafficSource())).append('\n')
                .append("- 주문번호: ").append(field(event.orderNumber()));

        // 값마다 끊어 두었으니 여기까지 오면 넘칠 일이 거의 없다. 그래도
        // 남겨 둔다 — 항목이 늘어날 때 이 한도를 다시 세어 볼 사람은 없다.
        return clip(text.toString(), MAX_LENGTH);
    }

    /**
     * 입금 안내 문자가 못 나갔다는 알림.
     *
     * 신규 신청 알림과 나눠 둔다. 저쪽은 "주문이 들어왔다"이고 이쪽은 "이
     * 사람은 낼 방법이 없다"이다. 한 문장으로 묶으면 읽는 사람이 급한 것을
     * 골라내지 못한다.
     *
     * 연락처를 싣는다. 받는 사람이 이 알림 하나로 직접 안내할 수 있어야
     * 하고, 신규 신청 알림도 대표님 판단으로 같은 값을 싣는다.
     */
    public static String paymentGuideFailed(GoodsOrderSubmittedEvent event) {
        StringBuilder text = new StringBuilder()
                .append("⚠️ <b>입금 안내 문자를 보내지 못했습니다</b>\n")
                .append("- 주문번호: ").append(field(event.orderNumber())).append('\n')
                .append("- 이름: ").append(field(event.guardianName())).append('\n')
                .append("- 연락처: ").append(field(event.phone())).append('\n')
                .append("- 결제: ").append(WON.format(event.paymentAmountKrw())).append("원\n")
                .append("위 연락처로 계좌를 직접 안내해 주세요. ")
                .append("안내하지 않으면 기한이 지나 주문이 사라집니다.");
        return clip(text.toString(), MAX_LENGTH);
    }

    private static String field(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return clip(escape(value), MAX_FIELD_LENGTH);
    }

    /**
     * HTML 에서 뜻을 갖는 글자를 글자 그대로 만든다.
     *
     * 앰퍼샌드를 먼저 바꾼다. 나중에 바꾸면 우리가 방금 만든 &amp;lt; 의
     * 앰퍼샌드까지 다시 바꿔 &amp;amp;lt; 가 된다.
     */
    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * 길이를 맞추되 이스케이프한 글자 가운데를 자르지 않는다.
     *
     * &amp;amp; 를 &amp;am 에서 끊으면 텔레그램이 열리다 만 것으로 보고 400 을
     * 돌려준다. 길이를 줄이려다 알림을 통째로 잃는다.
     */
    private static String clip(String escaped, int max) {
        if (escaped.length() <= max) {
            return escaped;
        }
        String cut = escaped.substring(0, max);
        int lastAmp = cut.lastIndexOf('&');
        if (lastAmp >= 0 && cut.indexOf(';', lastAmp) < 0) {
            cut = cut.substring(0, lastAmp);
        }
        return cut;
    }
}
