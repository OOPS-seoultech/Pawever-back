package com.pawever.backend.notification.telegram;

import com.pawever.backend.goodssurvey.event.GoodsOrderSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 굿즈 신청이 접수되면 팀 채널로 알린다.
 *
 * 커밋된 뒤에만(AFTER_COMMIT) 받는다. 트랜잭션 안에서 보내면 두 가지가
 * 어긋난다 — 뒤에서 롤백된 신청에도 알림이 나가고, 텔레그램이 느린 동안
 * DB 커넥션을 붙잡고 있는다.
 *
 * 거기에 @Async 로 요청 스레드에서도 뗀다. 알림이 늦는 것과 신청자가 결과
 * 화면을 늦게 보는 것은 다른 문제다.
 *
 * 그래서 여기서 무엇이 실패하든 접수에는 아무 일도 일어나지 않는다. 접수는
 * 이미 끝났다. 대신 반드시 로그를 남긴다 — 멈춘 줄 모르는 알림은 없는
 * 알림보다 나쁘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoodsOrderTelegramListener {

    private final TelegramClient telegramClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGoodsOrderSubmitted(GoodsOrderSubmittedEvent event) {
        try {
            telegramClient.sendHtml(TelegramMessage.goodsOrderSubmitted(event));
        } catch (RuntimeException e) {
            // 이름과 연락처가 담긴 값이라 내용은 로그에 남기지 않는다.
            log.error("굿즈 신청 알림을 만들지 못했다: 주문 {}", event.orderNumber(), e);
        }
    }
}
