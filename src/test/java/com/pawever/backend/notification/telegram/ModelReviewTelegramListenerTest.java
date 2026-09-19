package com.pawever.backend.notification.telegram;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.pawever.backend.workflow.event.ModelReviewRequestedEvent;
import org.junit.jupiter.api.Test;

class ModelReviewTelegramListenerTest {

  @Test
  void 모델링_제출이_완료되면_검수용_봇에_주문번호만_알린다() {
    TelegramOpsClient client = mock(TelegramOpsClient.class);
    ModelReviewTelegramListener listener = new ModelReviewTelegramListener(client);
    ModelReviewRequestedEvent event = new ModelReviewRequestedEvent("PE-2026-000101", 42L);

    listener.onModelReviewRequested(event);

    verify(client).sendHtml(TelegramMessage.modelReviewRequested(event));
  }
}
