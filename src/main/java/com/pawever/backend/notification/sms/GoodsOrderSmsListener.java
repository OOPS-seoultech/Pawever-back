package com.pawever.backend.notification.sms;

import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import com.pawever.backend.goodssurvey.event.GoodsOrderSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 굿즈 신청이 접수되면 신청자에게 입금 계좌를 문자로 보낸다.
 *
 * 화면은 신청이 끝나자마자 "입력하신 연락처로 입금 계좌를 문자로 보내드립니다"
 * 라고 약속한다. 그 약속을 지키는 곳이 여기다. 이것이 없던 동안 신청자는
 * 낼 방법이 없었고, 2026-08-30 접수된 첫 주문이 그대로 만료됐다.
 *
 * 팀 채널 알림(GoodsOrderTelegramListener)과 같은 이벤트를 받지만 하는 일이
 * 다르다. 저쪽은 우리가 알기 위한 것이고 이쪽은 신청자가 돈을 내기 위한
 * 것이다. 둘을 한 자리에 묶으면 한쪽이 실패할 때 다른 쪽까지 멈춘다.
 *
 * 커밋된 뒤에만(AFTER_COMMIT) 받는다. 트랜잭션 안에서 보내면 뒤에서 롤백된
 * 신청에도 계좌가 나간다 — 없는 주문의 입금이 들어오는 것이 가장 나쁘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoodsOrderSmsListener {

    private final SmsClient smsClient;
    private final SmsProperties smsProperties;
    private final GoodsSurveyProperties goodsSurveyProperties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGoodsOrderSubmitted(GoodsOrderSubmittedEvent event) {
        try {
            String message = PaymentGuideMessage.of(
                    event,
                    smsProperties.getBank(),
                    goodsSurveyProperties.getPaymentWindowMinutes()
            );
            boolean sent = smsClient.sendLms(event.phone(), PaymentGuideMessage.TITLE, message);
            if (!sent) {
                // 결제 수단이 끊긴 것이라 알림 실패와 무게가 다르다. 사람이
                // 주문번호로 찾아 직접 안내할 수 있도록 번호만 남긴다.
                log.error("입금 안내를 보내지 못했다. 수동 안내 필요: 주문 {}", event.orderNumber());
            }
        } catch (RuntimeException e) {
            log.error("입금 안내 문자를 만들지 못했다: 주문 {}", event.orderNumber(), e);
        }
    }
}
