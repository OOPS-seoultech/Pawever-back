package com.pawever.backend.workflow.notification;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 승인 설정이 완성된 경우에만 outbox를 외부 제공자에 전달한다. */
@Component
@RequiredArgsConstructor
public class ShipmentNotificationWorker {
  private final ShipmentNotificationEventRepository events;
  private final ShipmentNotificationProcessor processor;
  private final ShipmentNotificationProperties properties;
  private final Clock clock;

  @Scheduled(fixedDelayString = "${notification.alimtalk.worker-delay-ms:60000}")
  public void poll() {
    if (!properties.isReady()) return;
    for (Long id : events.findReady(clock.instant(), PageRequest.of(0, 50))) {
      processor.process(id);
    }
  }
}
