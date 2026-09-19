package com.pawever.backend.notification.telegram;

import com.pawever.backend.workflow.event.ModelReviewRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 검수 대기 확정 뒤에만 운영 봇으로 알린다. Telegram에서는 검수 결정을 받지 않는다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelReviewTelegramListener {
  private final TelegramOpsClient telegramOpsClient;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onModelReviewRequested(ModelReviewRequestedEvent event) {
    try {
      telegramOpsClient.sendHtml(TelegramMessage.modelReviewRequested(event));
    } catch (RuntimeException e) {
      log.error("모델 검수 알림을 만들지 못했다: 주문 {}", event.orderNumber(), e);
    }
  }
}
