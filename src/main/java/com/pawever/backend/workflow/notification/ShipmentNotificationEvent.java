package com.pawever.backend.workflow.notification;

import com.pawever.backend.global.common.BaseTimeEntity;
import com.pawever.backend.global.common.EncryptedStringConverter;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.workflow.WorkflowException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 한 주문의 발송 알림 결과. 요청 접수와 고객 수신 성공을 분리해 보존한다. */
@Entity
@Table(name = "shipment_notification_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentNotificationEvent extends BaseTimeEntity {
  private static final Pattern UNRESOLVED_VARIABLE = Pattern.compile("#\\{[^}]+}");

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private long version;

  @Column(nullable = false)
  private Long batchId;

  @Column(nullable = false, length = 20)
  private String orderNumber;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(nullable = false, length = 1000)
  private String recipientPhone;

  @Column(nullable = false, length = 64)
  private String recipientHash;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(length = 1000)
  private String guardianName;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(length = 1000)
  private String petName;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(length = 2000)
  private String shippingAddress;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(length = 2000)
  private String shippingAddressDetail;

  @Column(length = 20)
  private String postalCode;

  @Column(nullable = false, length = 20)
  private String trackingNumber;

  @Column(length = 100)
  private String templateCode;

  @Column(length = 64)
  private String templateBodyHash;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(length = 4000)
  private String renderedBody;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(length = 100)
  private String providerMessageId;

  private int sendAttempts;
  private int resultChecks;

  @Column(length = 80)
  private String lastResultCode;

  @Column(length = 300)
  private String lastResultMessage;

  private Instant acceptedAt;
  private Instant succeededAt;
  private Instant failedAt;
  private Instant nextCheckAt;

  @Column(length = 300)
  private String lastRetryReason;

  private Instant lastRetriedAt;

  public static ShipmentNotificationEvent of(
      Long batchId,
      GoodsSurveyFulfillment order,
      String recipientHash,
      ShipmentNotificationProperties properties) {
    var event = new ShipmentNotificationEvent();
    event.batchId = batchId;
    event.orderNumber = order.getOrderNumber();
    event.recipientPhone = order.getPhone();
    event.recipientHash = recipientHash;
    event.guardianName = order.getGuardianName();
    event.petName = order.getPetName();
    event.shippingAddress = order.getAddress();
    event.shippingAddressDetail = order.getAddressDetail();
    event.postalCode = order.getPostalCode();
    event.trackingNumber = order.getTrackingNumber();
    event.status = properties.isReady() ? "PENDING" : "PENDING_CONFIGURATION";
    if (properties.isReady()) {
      event.applyTemplate(properties);
    }
    return event;
  }

  public void activate(ShipmentNotificationProperties properties) {
    if (!"PENDING_CONFIGURATION".equals(status) || !properties.isReady()) return;
    applyTemplate(properties);
    status = "PENDING";
  }

  public void recordAccepted(
      String messageId, String resultCode, String resultMessage, Instant at) {
    sendAttempts++;
    providerMessageId = messageId;
    status = "ACCEPTED";
    acceptedAt = at;
    updateResult(resultCode, resultMessage);
    nextCheckAt = at.plusSeconds(60);
  }

  public void recordSendFailed(String resultCode, String resultMessage, Instant at) {
    sendAttempts++;
    status = "FAILED";
    failedAt = at;
    nextCheckAt = null;
    updateResult(resultCode, resultMessage);
  }

  public void recordSendUnknown(String resultCode, String resultMessage, Instant at) {
    sendAttempts++;
    status = "UNKNOWN";
    nextCheckAt = null;
    updateResult(resultCode, resultMessage);
  }

  public void recordResultUnknown(String resultCode, String resultMessage, Instant at) {
    resultChecks++;
    status = "UNKNOWN";
    nextCheckAt = at.plusSeconds(300);
    updateResult(resultCode, resultMessage);
  }

  public void recordResultFailed(String resultCode, String resultMessage, Instant at) {
    resultChecks++;
    status = "FAILED";
    failedAt = at;
    nextCheckAt = null;
    updateResult(resultCode, resultMessage);
  }

  public void recordSucceeded(String resultCode, String resultMessage, Instant at) {
    resultChecks++;
    status = "SUCCEEDED";
    succeededAt = at;
    nextCheckAt = null;
    updateResult(resultCode, resultMessage);
  }

  public void retry(Instant at, String reason) {
    if (!"FAILED".equals(status)) {
      throw new WorkflowException(409, "NOT_RETRYABLE", "실패한 알림만 재시도할 수 있습니다.");
    }
    if (reason == null || reason.isBlank()) {
      throw new WorkflowException(400, "REASON_REQUIRED", "재시도 사유를 입력해 주세요.");
    }
    status = "PENDING";
    providerMessageId = null;
    acceptedAt = null;
    failedAt = null;
    nextCheckAt = null;
    lastResultCode = null;
    lastResultMessage = null;
    lastRetryReason = clip(reason);
    lastRetriedAt = at;
  }

  public boolean isMessageReady() {
    return renderedBody != null
        && !renderedBody.isBlank()
        && !UNRESOLVED_VARIABLE.matcher(renderedBody).find();
  }

  private void applyTemplate(ShipmentNotificationProperties properties) {
    templateCode = properties.getTemplateCode();
    templateBodyHash = properties.getApprovedBodySha256();
    renderedBody =
        properties
            .body()
            .replace("#{반려인}", value(guardianName))
            .replace("#{보호자이름}", value(guardianName))
            .replace("#{반려견이름}", value(petName))
            .replace("#{택배사}", "우체국 준등기")
            .replace("#{송장번호}", value(trackingNumber))
            .replace("#{연락처}", value(recipientPhone))
            .replace("#{주문번호}", value(orderNumber))
            .replace("#{주소}", value(shippingAddress))
            .replace("#{상세주소}", value(shippingAddressDetail))
            .replace("#{우편번호}", value(postalCode));
  }

  private void updateResult(String code, String message) {
    lastResultCode = code == null ? null : clip(code);
    lastResultMessage = message == null ? null : clip(message);
  }

  private static String clip(String value) {
    String stripped = value.strip();
    return stripped.length() <= 300 ? stripped : stripped.substring(0, 300);
  }

  private static String value(String value) {
    return value == null ? "" : value;
  }
}
