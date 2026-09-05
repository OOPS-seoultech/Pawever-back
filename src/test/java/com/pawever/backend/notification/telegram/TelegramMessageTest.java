package com.pawever.backend.notification.telegram;

import com.pawever.backend.goodssurvey.event.GoodsOrderSubmittedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 텔레그램에 보낼 문장을 만드는 자리.
 *
 * 대표님이 준 예시 코드는 parse_mode 를 HTML 로 두고 신청 내용을 그대로
 * 끼워 넣었다. 그러면 신청자가 적은 글자 하나에 알림 전체가 사라진다 —
 * "키링 <2개>" 나 "A&B" 같은 것이 들어오면 텔레그램이 400 을 돌려주고
 * 그 신청만 조용히 알림이 안 간다. 굿즈 직접 입력과 보호자 이름은 자유
 * 입력이라 실제로 들어올 수 있는 값이다.
 *
 * 여기서 붙드는 것은 그 한 가지다. 형식은 대표님 예시를 따르되, 문의내용
 * 자리에는 굿즈 주문에 실제로 있는 값을 넣는다.
 */
class TelegramMessageTest {

    private static GoodsOrderSubmittedEvent event(String guardianName, String goodsLabel) {
        return new GoodsOrderSubmittedEvent(
                "PW-1042",
                guardianName,
                "010-1234-5678",
                "뭉치",
                goodsLabel,
                26_900,
                true,
                "instagram / cpc"
        );
    }

    @Test
    void 대표님_예시_형식을_따른다() {
        String text = TelegramMessage.goodsOrderSubmitted(event("황성욱", "3D 전신 피규어"));

        assertThat(text).contains("신규 굿즈 신청");
        assertThat(text).contains("이름: 황성욱");
        assertThat(text).contains("연락처: 010-1234-5678");
        assertThat(text).contains("유입경로: instagram / cpc");
    }

    @Test
    void 입금_안내가_못_나갔으면_무엇을_해야_하는지_적는다() {
        // 이 문자는 알림이 아니라 결제 수단이다. 못 나가면 신청자는 계좌를
        // 못 받고 주문은 시간이 지나 사라진다. 받는 사람이 이 알림 하나로
        // 직접 안내할 수 있어야 한다.
        String text = TelegramMessage.paymentGuideFailed(event("황성욱", "3D 전신 피규어"));

        assertThat(text).contains("입금 안내 문자");
        assertThat(text).contains("PW-1042");
        assertThat(text).contains("010-1234-5678");
        assertThat(text).contains("황성욱");
    }

    @Test
    void 실패_알림도_꺾쇠와_앰퍼샌드를_피해서_적는다() {
        // 우리가 적은 <b> 는 태그로 두고, 사람이 적은 값만 글자로 만든다.
        String text = TelegramMessage.paymentGuideFailed(event("A&B <b>", "피규어"));

        assertThat(text).contains("A&amp;B &lt;b&gt;");
        assertThat(text).doesNotContain("A&B");
    }

    @Test
    void 꺾쇠와_앰퍼샌드를_피해서_적는다() {
        // 이스케이프하지 않으면 텔레그램이 400 을 돌려주고 이 신청은
        // 알림이 통째로 안 간다.
        String text = TelegramMessage.goodsOrderSubmitted(event("황성욱", "키링 <2개> A&B"));

        assertThat(text).contains("키링 &lt;2개&gt; A&amp;B");
        assertThat(text).doesNotContain("키링 <2개>");
    }

    @Test
    void 태그를_심어도_태그로_읽히지_않는다() {
        String text = TelegramMessage.goodsOrderSubmitted(event("<b>굵게</b>", "피규어"));

        assertThat(text).contains("&lt;b&gt;굵게&lt;/b&gt;");
    }

    @Test
    void 제목의_굵게는_살려_둔다() {
        // 값만 피하고 우리가 적은 태그는 그대로 둔다.
        String text = TelegramMessage.goodsOrderSubmitted(event("황성욱", "피규어"));

        assertThat(text).contains("<b>");
    }

    @Test
    void 금액을_읽기_쉽게_끊는다() {
        String text = TelegramMessage.goodsOrderSubmitted(event("황성욱", "피규어"));

        assertThat(text).contains("26,900원");
    }

    @Test
    void 설문_참여_여부를_밝힌다() {
        // 얼마를 받을지가 여기서 갈린다. 제작팀이 먼저 알아야 하는 값이다.
        String participant = TelegramMessage.goodsOrderSubmitted(event("황성욱", "피규어"));
        assertThat(participant).contains("설문 참여");

        GoodsOrderSubmittedEvent walkIn = new GoodsOrderSubmittedEvent(
                "PW-1043", "황성욱", "010-1234-5678", "뭉치",
                "피규어", 32_900, false, "직접 유입"
        );
        assertThat(TelegramMessage.goodsOrderSubmitted(walkIn)).doesNotContain("설문 참여");
    }

    @Test
    void 텔레그램_길이_제한을_넘지_않는다() {
        // 텔레그램은 4096자를 넘으면 받지 않는다. 자유 입력이 있는 한
        // 길이는 우리가 정하는 값이 아니다.
        String text = TelegramMessage.goodsOrderSubmitted(event("황성욱", "가".repeat(5000)));

        assertThat(text.length()).isLessThanOrEqualTo(4096);
    }

    @Test
    void 잘라낸_뒤에도_태그가_깨지지_않는다() {
        // 이스케이프한 글자 가운데를 자르면 &am 같은 조각이 남아 400 이 난다.
        String text = TelegramMessage.goodsOrderSubmitted(event("황성욱", "&".repeat(5000)));

        assertThat(text.length()).isLessThanOrEqualTo(4096);
        assertThat(text).doesNotContain("&am\n");
        assertThat(text.endsWith("&") || text.endsWith("&a") || text.endsWith("&am")).isFalse();
    }
}
