package com.pawever.backend.notification.sms;

import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import com.pawever.backend.goodssurvey.event.GoodsOrderSubmittedEvent;
import com.pawever.backend.notification.telegram.TelegramClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 입금 안내 문자가 못 나갔을 때 사람이 알게 되는지.
 *
 * 이 문자는 알림이 아니라 결제 수단이다. 못 나가면 신청자는 계좌를 못 받고
 * 주문은 시간이 지나 사라진다. 지금까지는 실패가 서버 로그에만 남았는데,
 * 현장에서 로그를 보는 사람은 없다. 팀 채널로 주문번호를 알려야 그 자리에서
 * 직접 안내할 수 있다.
 */
@ExtendWith(MockitoExtension.class)
class GoodsOrderSmsListenerTest {

    private static final GoodsOrderSubmittedEvent ORDER = new GoodsOrderSubmittedEvent(
            "PE-2026-000123",
            "황성욱",
            "01012345678",
            "보리",
            "3D 전신 피규어",
            11_900,
            false,
            "qr",
            180
    );

    @Mock private SmsClient smsClient;
    @Mock private TelegramClient telegramClient;

    private GoodsOrderSmsListener listener;

    @BeforeEach
    void setUp() {
        SmsProperties smsProperties = new SmsProperties();
        smsProperties.getBank().setName("IBK기업은행");
        smsProperties.getBank().setAccount("256-126343-04-019");
        smsProperties.getBank().setHolder("이종무");
        listener = new GoodsOrderSmsListener(
                smsClient, smsProperties, new GoodsSurveyProperties(), telegramClient);
    }

    @Test
    void 문자가_거절되면_팀_채널에_주문번호를_알린다() {
        when(smsClient.sendLms(anyString(), anyString(), anyString())).thenReturn(false);

        listener.onGoodsOrderSubmitted(ORDER);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendHtml(text.capture());
        assertThat(text.getValue())
                .contains("입금 안내 문자")
                .contains("PE-2026-000123")
                // 직접 안내하려면 누구에게 걸어야 하는지 있어야 한다. 신규 신청
                // 알림도 대표님 판단으로 같은 값을 싣는다.
                .contains("01012345678");
    }

    @Test
    void 문자가_나갔으면_팀_채널을_건드리지_않는다() {
        // 신규 신청 알림이 이미 따로 간다. 여기서 또 보내면 한 주문에 두 번 울린다.
        when(smsClient.sendLms(anyString(), anyString(), anyString())).thenReturn(true);

        listener.onGoodsOrderSubmitted(ORDER);

        verify(telegramClient, never()).sendHtml(any());
    }

    @Test
    void 문자_전송이_예외로_끝나도_팀_채널에는_알린다() {
        when(smsClient.sendLms(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("boom"));

        listener.onGoodsOrderSubmitted(ORDER);

        verify(telegramClient).sendHtml(anyString());
    }
}
