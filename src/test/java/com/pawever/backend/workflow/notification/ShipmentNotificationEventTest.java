package com.pawever.backend.workflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.workflow.WorkflowException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ShipmentNotificationEventTest {
  @Test
  void acceptedIsNotSucceededAndOnlyFailedCanBeRetried() {
    var event = readyEvent();
    var acceptedAt = Instant.parse("2026-09-16T10:00:00Z");

    event.recordAccepted("mid-1", "0", "accepted", acceptedAt);

    assertThat(event.getStatus()).isEqualTo("ACCEPTED");
    assertThat(event.getSucceededAt()).isNull();
    assertThatThrownBy(() -> event.retry(acceptedAt, "아직 처리 중"))
        .isInstanceOf(WorkflowException.class);

    event.recordResultFailed("9999", "수신 실패", acceptedAt.plusSeconds(60));
    event.retry(acceptedAt.plusSeconds(120), "관리자 재시도");

    assertThat(event.getStatus()).isEqualTo("PENDING");
    assertThat(event.getProviderMessageId()).isNull();
    assertThat(event.getLastResultCode()).isNull();
    assertThat(event.getLastResultMessage()).isNull();
  }

  @Test
  void finalSuccessCannotBeRetried() {
    var event = readyEvent();
    var now = Instant.parse("2026-09-16T10:00:00Z");
    event.recordAccepted("mid-1", "0", "accepted", now);
    event.recordSucceeded("0000", "success", now.plusSeconds(60));

    assertThat(event.getStatus()).isEqualTo("SUCCEEDED");
    assertThatThrownBy(() -> event.retry(now.plusSeconds(120), "다시"))
        .isInstanceOf(WorkflowException.class);
  }

  @Test
  void unknownApprovedTemplateVariableCannotBeSent() {
    var properties = readyProperties("#{알수없는변수} #{송장번호}");
    var order = order();
    var event =
        ShipmentNotificationEvent.of(
            1L, order, ShipmentNotificationService.phoneHash(order.getPhone()), properties);

    assertThat(event.isMessageReady()).isFalse();
  }

  private ShipmentNotificationEvent readyEvent() {
    var properties = readyProperties("#{보호자이름}님 #{반려견이름} #{송장번호}");
    var order = order();
    return ShipmentNotificationEvent.of(
        1L, order, ShipmentNotificationService.phoneHash(order.getPhone()), properties);
  }

  private ShipmentNotificationProperties readyProperties(String body) {
    var properties = new ShipmentNotificationProperties();
    properties.setEnabled(true);
    properties.setApiKey("key");
    properties.setUserId("user");
    properties.setSenderKey("sender-key");
    properties.setSender("0212345678");
    properties.setTemplateCode("template");
    properties.setSubject("subject");
    properties.setApprovedBody(body);
    properties.setApprovedBodySha256(
        ShipmentNotificationProperties.hash(properties.getApprovedBody()));
    properties.setSuccessResultCodes(List.of("0000"));
    return properties;
  }

  private GoodsSurveyFulfillment order() {
    var order = Mockito.mock(GoodsSurveyFulfillment.class);
    when(order.getOrderNumber()).thenReturn("PE-2026-000001");
    when(order.getPhone()).thenReturn("010-1234-5678");
    when(order.getGuardianName()).thenReturn("보호자");
    when(order.getPetName()).thenReturn("초코");
    when(order.getTrackingNumber()).thenReturn("1234567890123");
    when(order.getAddress()).thenReturn("서울시 공릉로");
    when(order.getAddressDetail()).thenReturn("101호");
    when(order.getPostalCode()).thenReturn("01234");
    return order;
  }
}
