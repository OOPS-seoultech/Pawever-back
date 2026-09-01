package com.pawever.backend.notification.sms;

import com.pawever.backend.goodssurvey.event.GoodsOrderSubmittedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 입금 안내 문장.
 *
 * 받는 사람이 이 문자 하나로 입금을 마칠 수 있어야 한다. 되묻는 연락이
 * 오가는 사이 결제 대기 시간이 지나가기 때문이다.
 */
class PaymentGuideMessageTest {

    private static final GoodsOrderSubmittedEvent ORDER = new GoodsOrderSubmittedEvent(
            "PE-2026-000123",
            "황성욱",
            "01012345678",
            "보리",
            "3D 전신 피규어",
            32900,
            false,
            "instagram"
    );

    private static SmsProperties.Bank bank() {
        SmsProperties.Bank bank = new SmsProperties.Bank();
        bank.setName("국민은행");
        bank.setAccount("123456-78-901234");
        bank.setHolder("포에버");
        return bank;
    }

    @Test
    void 입금에_필요한_넷을_모두_담는다() {
        String message = PaymentGuideMessage.of(ORDER, bank(), 30);

        assertThat(message)
                .contains("국민은행 123456-78-901234")
                .contains("예금주 포에버")
                .contains("32,900원")
                .contains("PE-2026-000123")
                .contains("30분");
    }

    @Test
    void 입금자명을_따로_적어_대조할_수_있게_한다() {
        // 무통장 입금은 들어온 돈에 주문번호가 붙어 오지 않는다. 이름이
        // 어긋나면 어느 주문의 돈인지 사람이 찾아야 하고, 찾는 동안 만료된다.
        assertThat(PaymentGuideMessage.of(ORDER, bank(), 30))
                .contains("입금자명 황성욱");
    }

    @Test
    void 기한을_넘기면_사진까지_사라진다고_밝힌다() {
        // 서버는 만료된 주문의 사진을 실제로 파기한다. 그 사실을 알리지 않으면
        // 돈만 늦게 낸 줄 알았던 사람이 사진이 사라진 것을 뒤늦게 안다.
        assertThat(PaymentGuideMessage.of(ORDER, bank(), 30))
                .contains("사진도 함께 파기");
    }

    @Test
    void 결제_대기_시간이_바뀌면_문구도_따라간다() {
        // 설정값을 문장에 손으로 적으면 둘이 갈린다. 서버는 45분을 기다리는데
        // 문자는 30분이라고 하면, 그 15분 동안 받은 문의는 전부 우리 잘못이다.
        assertThat(PaymentGuideMessage.of(ORDER, bank(), 180)).contains("180분");
    }

    @Test
    void 광고_표기와_수신거부를_넣지_않는다() {
        // 주문 처리 안내라 광고가 아니다. 넣으면 오히려 거짓이 되고, 수신거부한
        // 사람에게 계좌를 못 보내는 일이 생긴다.
        assertThat(PaymentGuideMessage.of(ORDER, bank(), 30))
                .doesNotContain("(광고)")
                .doesNotContain("무료수신거부");
    }

    @Test
    void 이름이_아주_길어도_계좌가_밀려나지_않는다() {
        // 보호자 이름은 자유 입력이다. 한 칸이 길다고 뒤가 잘리면 하필
        // 계좌번호가 사라진다.
        GoodsOrderSubmittedEvent longName = new GoodsOrderSubmittedEvent(
                ORDER.orderNumber(), "가".repeat(500), ORDER.phone(), ORDER.petName(),
                ORDER.goodsLabel(), ORDER.paymentAmountKrw(), false, ORDER.trafficSource()
        );

        String message = PaymentGuideMessage.of(longName, bank(), 30);
        assertThat(message)
                .contains("국민은행 123456-78-901234")
                .hasSizeLessThanOrEqualTo(PaymentGuideMessage.MAX_LENGTH);
    }

    @Test
    void 설문_참여자는_할인가가_그대로_적힌다() {
        // 금액은 서버가 정해 이벤트에 실어 준다. 문장이 다시 계산하면 서버가
        // 기록한 값과 문자로 안내한 값이 갈린다.
        GoodsOrderSubmittedEvent member = new GoodsOrderSubmittedEvent(
                ORDER.orderNumber(), ORDER.guardianName(), ORDER.phone(), ORDER.petName(),
                ORDER.goodsLabel(), 26900, true, ORDER.trafficSource()
        );

        assertThat(PaymentGuideMessage.of(member, bank(), 30)).contains("26,900원");
    }
}
