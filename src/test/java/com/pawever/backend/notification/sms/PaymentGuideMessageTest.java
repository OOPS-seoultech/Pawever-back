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
            "instagram",
            2880
    );

    private static SmsProperties.Bank bank() {
        SmsProperties.Bank bank = new SmsProperties.Bank();
        bank.setName("국민은행");
        bank.setAccount("123456-78-901234");
        bank.setHolder("포에버");
        return bank;
    }

    /**
     * 확정본 그대로인지.
     *
     * 이 문자는 선아님이 검토해 확정한 문구다. 한 줄씩 담겼는지만 보면 순서와
     * 줄바꿈이 흐트러져도 통과한다 — 확정을 받은 것은 문장 하나가 아니라 이
     * 배치 전체다. 그래서 통째로 못 박는다.
     *
     * 이 시험이 깨지면 코드를 고칠 것이 아니라 확정본을 다시 받아야 한다는 뜻이다.
     */
    @Test
    void 확정받은_문구_그대로_보낸다() {
        String message = PaymentGuideMessage.of(ORDER, bank(), 2880);

        assertThat(message).isEqualTo(
                """
                [포에버] 굿즈 주문 입금 안내

                안녕하세요, 황성욱님.
                포에버를 믿고 소중한 아이의 굿즈를 신청해주셔서 감사합니다.

                아래 계좌로 입금해주시면 제작이 진행됩니다.

                ◼︎ 주문번호 : PE-2026-000123
                ◼︎ 입금 금액 : 32,900원

                ◼︎ 입금 계좌 : 국민은행 123456-78-901234 포에버
                ◼︎ 입금자명 : 황성욱

                2일 안에 입금이 확인되지 않으면 주문이 자동으로 취소되고 보내주신 사진도 함께 파기됩니다.

                굿즈 신청 시 작성해주신 이름과 동일한 이름으로 입금 부탁드립니다.
                다른 이름으로 입금하실 경우 확인이 늦어질 수 있습니다.

                ◼︎ 문의는 문자 회신 대신 pawever01@gmail.com로 부탁드립니다!"""
        );
    }

    /**
     * 계좌는 확정본에 적힌 값을 박지 않고 설정에서 온다.
     *
     * 확정본에는 실제 계좌가 적혀 있다. 그대로 옮겨 적으면 계좌가 바뀌는 날
     * 문자만 옛 계좌를 부른다 — 그 돈은 우리 계좌로 오지 않는다.
     */
    @Test
    void 계좌가_바뀌면_문자도_따라간다() {
        SmsProperties.Bank moved = new SmsProperties.Bank();
        moved.setName("IBK기업은행");
        moved.setAccount("256-126343-04-019");
        moved.setHolder("이종무");

        assertThat(PaymentGuideMessage.of(ORDER, moved, 2880))
                .contains("◼︎ 입금 계좌 : IBK기업은행 256-126343-04-019 이종무");
    }

    @Test
    void 입금에_필요한_넷을_모두_담는다() {
        String message = PaymentGuideMessage.of(ORDER, bank(), 30);

        assertThat(message)
                .contains("◼︎ 입금 계좌 : 국민은행 123456-78-901234 포에버")
                .contains("32,900원")
                .contains("PE-2026-000123")
                .contains("30분");
    }

    @Test
    void 입금자명을_따로_적어_대조할_수_있게_한다() {
        // 무통장 입금은 들어온 돈에 주문번호가 붙어 오지 않는다. 이름이
        // 어긋나면 어느 주문의 돈인지 사람이 찾아야 하고, 찾는 동안 만료된다.
        assertThat(PaymentGuideMessage.of(ORDER, bank(), 30))
                .contains("◼︎ 입금자명 : 황성욱");
    }

    @Test
    void 기한을_넘기면_사진까지_사라진다고_밝힌다() {
        // 서버는 만료된 주문의 사진을 실제로 파기한다. 그 사실을 알리지 않으면
        // 돈만 늦게 낸 줄 알았던 사람이 사진이 사라진 것을 뒤늦게 안다.
        assertThat(PaymentGuideMessage.of(ORDER, bank(), 30))
                .contains("사진도 함께 파기");
    }

    @Test
    void 주문이_들고_온_기한을_그대로_적는다() {
        // 통로마다 기한이 다르다. 설정 하나를 읽으면 플리마켓 주문에도
        // 상시 판매의 48시간이 적힌다.
        GoodsOrderSubmittedEvent flea = new GoodsOrderSubmittedEvent(
                "PE-2026-000201",
                "황성욱",
                "01012345678",
                "보리",
                "3D 전신 피규어",
                11_900,
                false,
                "qr",
                180
        );

        assertThat(PaymentGuideMessage.of(flea, bank(), flea.paymentWindowMinutes()))
                .contains("3시간 안에");
    }

    @Test
    void 결제_대기_시간이_바뀌면_문구도_따라간다() {
        // 설정값을 문장에 손으로 적으면 둘이 갈린다. 서버는 45분을 기다리는데
        // 문자는 30분이라고 하면, 그 15분 동안 받은 문의는 전부 우리 잘못이다.
        assertThat(PaymentGuideMessage.of(ORDER, bank(), 180)).contains("3시간");
    }

    @Test
    void 기다리는_시간을_사람이_읽는_말로_적는다() {
        // 설정은 분으로 들어온다. 그대로 적으면 "2880분 안에"가 되는데, 받는
        // 사람은 그게 언제까지인지 계산해야 한다.
        assertThat(PaymentGuideMessage.humanWindow(2880)).isEqualTo("2일");
        assertThat(PaymentGuideMessage.humanWindow(1440)).isEqualTo("1일");
        assertThat(PaymentGuideMessage.humanWindow(180)).isEqualTo("3시간");
        assertThat(PaymentGuideMessage.humanWindow(30)).isEqualTo("30분");
        // 딱 떨어지지 않으면 분 그대로 둔다. "1일 12시간"처럼 늘어놓는 것보다
        // 짧고, 이런 값을 쓸 일도 없다.
        assertThat(PaymentGuideMessage.humanWindow(90)).isEqualTo("90분");
    }

    @Test
    void 이틀로_설정하면_이틀이라고_말한다() {
        assertThat(PaymentGuideMessage.of(ORDER, bank(), 2880)).contains("2일 안에");
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
                ORDER.goodsLabel(), ORDER.paymentAmountKrw(), false, ORDER.trafficSource(),
                ORDER.paymentWindowMinutes()
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
                ORDER.goodsLabel(), 26900, true, ORDER.trafficSource(),
                ORDER.paymentWindowMinutes()
        );

        assertThat(PaymentGuideMessage.of(member, bank(), 30)).contains("26,900원");
    }
}
