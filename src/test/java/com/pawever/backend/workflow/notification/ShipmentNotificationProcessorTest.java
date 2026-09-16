package com.pawever.backend.workflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.workflow.WorkflowIssueRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ShipmentNotificationProcessorTest {
  private ShipmentNotificationEventRepository events;
  private AligoAlimtalkClient client;
  private GoodsSurveyFulfillmentRepository orders;
  private ShipmentNotificationProcessor processor;
  private ShipmentNotificationEvent event;

  @BeforeEach
  void setUp() {
    events = Mockito.mock(ShipmentNotificationEventRepository.class);
    client = Mockito.mock(AligoAlimtalkClient.class);
    orders = Mockito.mock(GoodsSurveyFulfillmentRepository.class);
    var issues = Mockito.mock(WorkflowIssueRepository.class);
    var properties = readyProperties();
    var order = order("010-1234-5678", "1234567890123");
    event = ShipmentNotificationEvent.of(1L, order, ShipmentNotificationService.phoneHash(order.getPhone()), properties);
    when(events.lockById(1L)).thenReturn(Optional.of(event));
    when(issues.findByOrderNumber("PE-2026-000001")).thenReturn(List.of());
    processor =
        new ShipmentNotificationProcessor(
            events,
            client,
            properties,
            orders,
            issues,
            Clock.fixed(Instant.parse("2026-09-16T10:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void providerAcceptanceDoesNotCountAsRecipientSuccess() {
    when(client.send(any(), any(), any()))
        .thenReturn(
            new AligoAlimtalkClient.ProviderResult(
                AligoAlimtalkClient.ResultStatus.ACCEPTED, "mid-1", "0", "accepted"));

    processor.process(1L);

    assertThat(event.getStatus()).isEqualTo("ACCEPTED");
    assertThat(event.getSucceededAt()).isNull();
    verify(orders, never()).lockByOrderNumber(any());
  }

  @Test
  void finalSuccessIsAppliedOnlyWhenRecipientAndTrackingStillMatch() {
    event.recordAccepted("mid-1", "0", "accepted", Instant.parse("2026-09-16T09:00:00Z"));
    when(client.query("mid-1", "010-1234-5678"))
        .thenReturn(
            new AligoAlimtalkClient.ProviderResult(
                AligoAlimtalkClient.ResultStatus.SUCCEEDED, "mid-1", "0000", "success"));
    var changedOrder = order("010-9999-9999", "1234567890123");
    when(orders.lockByOrderNumber("PE-2026-000001"))
        .thenReturn(Optional.of(changedOrder));

    processor.process(1L);

    assertThat(event.getStatus()).isEqualTo("FAILED");
    assertThat(event.getLastResultCode()).isEqualTo("RECIPIENT_CHANGED");
  }

  private GoodsSurveyFulfillment order(String phone, String tracking) {
    var order = Mockito.mock(GoodsSurveyFulfillment.class);
    when(order.getOrderNumber()).thenReturn("PE-2026-000001");
    when(order.getPhone()).thenReturn(phone);
    when(order.getGuardianName()).thenReturn("보호자");
    when(order.getPetName()).thenReturn("초코");
    when(order.getTrackingNumber()).thenReturn(tracking);
    return order;
  }

  private ShipmentNotificationProperties readyProperties() {
    var properties = new ShipmentNotificationProperties();
    properties.setEnabled(true);
    properties.setApiKey("key");
    properties.setUserId("user");
    properties.setSenderKey("sender-key");
    properties.setSender("0212345678");
    properties.setTemplateCode("template");
    properties.setSubject("subject");
    properties.setApprovedBody("#{보호자이름}님 #{반려견이름} #{송장번호}");
    properties.setApprovedBodySha256(
        ShipmentNotificationProperties.hash(properties.getApprovedBody()));
    properties.setSuccessResultCodes(List.of("0000"));
    return properties;
  }
}
