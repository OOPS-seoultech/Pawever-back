package com.pawever.backend.workflow.notification;

import static com.pawever.backend.admin.entity.PermissionKey.IMPORT_SHIPMENT_RESULTS;

import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.workflow.StaffPermissions;
import com.pawever.backend.workflow.WorkflowException;
import com.pawever.backend.workflow.WorkflowService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShipmentNotificationService {
  private final ShipmentNotificationBatchRepository batches;
  private final ShipmentNotificationEventRepository events;
  private final ShipmentNotificationProperties properties;
  private final StaffPermissions access;
  private final WorkflowService workflow;
  private final Clock clock;

  /** 송장 반영 transaction 안에서 호출한다. 같은 주문은 알림 이벤트를 하나만 가진다. */
  public Long enqueue(Long postalImportBatchId, GoodsSurveyFulfillment order) {
    var batch =
        batches
            .findByPostalImportBatchId(postalImportBatchId)
            .orElseGet(
                () ->
                    batches.saveAndFlush(
                        ShipmentNotificationBatch.of(
                            postalImportBatchId, access.current().getId(), clock.instant())));
    if (events
        .findByOrderNumberAndTrackingNumber(order.getOrderNumber(), order.getTrackingNumber())
        .isEmpty()) {
      events.saveAndFlush(
          ShipmentNotificationEvent.of(batch.getId(), order, phoneHash(order.getPhone()), properties));
    }
    return batch.getId();
  }

  public Optional<Long> batchIdForPostalImport(Long postalImportBatchId) {
    return batches.findByPostalImportBatchId(postalImportBatchId).map(ShipmentNotificationBatch::getId);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> read(Long id) {
    access.require(IMPORT_SHIPMENT_RESULTS);
    var batch =
        batches
            .findById(id)
            .orElseThrow(() -> new WorkflowException(404, "NOT_FOUND", "알림 묶음을 찾을 수 없습니다."));
    var all = events.findByBatchIdOrderByIdAsc(id);
    var view = new LinkedHashMap<String, Object>();
    view.put("id", batch.getId());
    view.put("postalImportBatchId", batch.getPostalImportBatchId());
    view.put("queuedAt", batch.getQueuedAt());
    view.put("configured", properties.isReady());
    view.put("pending", all.stream().filter(e -> pending(e.getStatus())).count());
    view.put("succeeded", all.stream().filter(e -> "SUCCEEDED".equals(e.getStatus())).count());
    view.put("failed", all.stream().filter(e -> "FAILED".equals(e.getStatus())).count());
    view.put(
        "events",
        all.stream()
            .map(this::eventView)
            .toList());
    return view;
  }

  @Transactional
  public Map<String, Object> retry(Long id, String reason) {
    access.require(IMPORT_SHIPMENT_RESULTS);
    var event =
        events
            .lockById(id)
            .orElseThrow(() -> new WorkflowException(404, "NOT_FOUND", "알림 이력을 찾을 수 없습니다."));
    String before = event.getStatus();
    event.retry(clock.instant(), reason);
    workflow.audit(
        event.getOrderNumber(),
        "RETRY_SHIPMENT_NOTIFICATION",
        before,
        event.getStatus(),
        reason);
    return eventView(event);
  }

  private Map<String, Object> eventView(ShipmentNotificationEvent event) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("id", event.getId());
    view.put("version", event.getVersion());
    view.put("orderNumber", event.getOrderNumber());
    view.put("trackingNumber", event.getTrackingNumber());
    view.put("status", event.getStatus());
    view.put("sendAttempts", event.getSendAttempts());
    view.put("resultChecks", event.getResultChecks());
    view.put("lastResultCode", event.getLastResultCode());
    view.put("lastResultMessage", event.getLastResultMessage());
    view.put("acceptedAt", event.getAcceptedAt());
    view.put("succeededAt", event.getSucceededAt());
    view.put("failedAt", event.getFailedAt());
    return view;
  }

  private static boolean pending(String status) {
    return status.equals("PENDING_CONFIGURATION")
        || status.equals("PENDING")
        || status.equals("ACCEPTED")
        || status.equals("UNKNOWN");
  }

  static String phoneHash(String phone) {
    try {
      String normalized = phone == null ? "" : phone.replaceAll("[^0-9]", "");
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(normalized.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
