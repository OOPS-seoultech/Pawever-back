package com.pawever.backend.workflow.notification;

import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.workflow.WorkflowIssue;
import com.pawever.backend.workflow.WorkflowIssueRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** outbox 한 건을 잠그고 외부 요청 또는 결과 조회를 수행한다. */
@Service
@RequiredArgsConstructor
public class ShipmentNotificationProcessor {
  private final ShipmentNotificationEventRepository events;
  private final AligoAlimtalkClient client;
  private final ShipmentNotificationProperties properties;
  private final GoodsSurveyFulfillmentRepository orders;
  private final WorkflowIssueRepository issues;
  private final Clock clock;

  @Transactional
  public void process(Long id) {
    var event = events.lockById(id).orElse(null);
    if (event == null || !properties.isReady()) return;
    event.activate(properties);
    if ("PENDING".equals(event.getStatus())) {
      if (!event.isMessageReady()) {
        event.recordSendFailed(
            "TEMPLATE_VARIABLE_INVALID", "승인 템플릿의 변수를 모두 채울 수 없습니다.", clock.instant());
        issue(event.getOrderNumber(), true);
        return;
      }
      applySend(event, client.send(event.getRecipientPhone(), properties.getSubject(), event.getRenderedBody()));
      return;
    }
    if (("ACCEPTED".equals(event.getStatus()) || "UNKNOWN".equals(event.getStatus()))
        && event.getProviderMessageId() != null) {
      applyResult(
          event, client.query(event.getProviderMessageId(), event.getRecipientPhone()));
    }
  }

  private void applySend(
      ShipmentNotificationEvent event, AligoAlimtalkClient.ProviderResult result) {
    var now = clock.instant();
    switch (result.status()) {
      case ACCEPTED ->
          event.recordAccepted(
              result.messageId(), result.resultCode(), result.resultMessage(), now);
      case FAILED -> {
        event.recordSendFailed(result.resultCode(), result.resultMessage(), now);
        issue(event.getOrderNumber(), true);
      }
      case UNKNOWN -> event.recordSendUnknown(result.resultCode(), result.resultMessage(), now);
      case SUCCEEDED ->
          // 발송 API는 최종 수신 성공을 반환하는 통로가 아니다.
          event.recordSendUnknown("INVALID_SEND_RESULT", "발송 응답 형식이 올바르지 않습니다.", now);
    }
  }

  private void applyResult(
      ShipmentNotificationEvent event, AligoAlimtalkClient.ProviderResult result) {
    var now = clock.instant();
    switch (result.status()) {
      case SUCCEEDED -> {
        var order = orders.lockByOrderNumber(event.getOrderNumber()).orElse(null);
        if (order == null
            || !event.getTrackingNumber().equals(order.getTrackingNumber())
            || !event.getRecipientHash().equals(ShipmentNotificationService.phoneHash(order.getPhone()))) {
          event.recordResultFailed(
              "RECIPIENT_CHANGED", "발송 후 주문의 수신자 또는 송장이 변경되었습니다.", now);
          issue(event.getOrderNumber(), true);
          return;
        }
        event.recordSucceeded(result.resultCode(), result.resultMessage(), now);
        issue(event.getOrderNumber(), false);
      }
      case FAILED -> {
        event.recordResultFailed(result.resultCode(), result.resultMessage(), now);
        issue(event.getOrderNumber(), true);
      }
      case UNKNOWN, ACCEPTED ->
          event.recordResultUnknown(result.resultCode(), result.resultMessage(), now);
    }
  }

  private void issue(String orderNumber, boolean present) {
    var existing =
        issues.findByOrderNumber(orderNumber).stream()
            .filter(issue -> "NOTIFICATION_FAILED".equals(issue.getCode()))
            .toList();
    if (present && existing.isEmpty()) issues.save(WorkflowIssue.of(orderNumber, "NOTIFICATION_FAILED"));
    if (!present && !existing.isEmpty()) issues.deleteAll(existing);
  }
}
