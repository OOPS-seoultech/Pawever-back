package com.pawever.backend.workflow;

import static com.pawever.backend.admin.entity.PermissionKey.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawever.backend.admin.entity.AdminRole;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class AdministrativeCompletionService {
  private final WorkflowService workflow;
  private final StaffPermissions access;
  private final WorkflowAuditRepository audits;
  private final com.pawever.backend.goodssurvey.service.GoodsOrderService orderHistory;
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  private void owner() {
    if (access.current().getRole() != AdminRole.OWNER)
      throw new WorkflowException(403, "FORBIDDEN", "소유자만 관리자 완료를 처리할 수 있습니다.");
    access.require(OVERRIDE_WORKFLOW);
    access.require(VIEW_ALL_ORDERS);
    access.require(VIEW_ORDER_BASIC);
  }

  private WorkflowException invalid(String message) {
    return new WorkflowException(400, "INVALID_INPUT", message);
  }

  private String reason(Map<String, Object> body) {
    if (!(body.get("reason") instanceof String value) || value.isBlank() || value.length() > 300)
      throw invalid("처리 사유를 1~300자로 입력해 주세요.");
    return value.strip();
  }

  private String encode(Object value) {
    try { return json.writeValueAsString(value); }
    catch (java.io.IOException e) { throw new IllegalStateException(e); }
  }

  public Map<String, Object> complete(String key, Map<String, Object> body) {
    owner();
    return workflow.command("administrative-completion", key, body, () -> {
      owner();
      String reason = reason(body);
      if (!(body.get("orders") instanceof List<?> entries) || entries.isEmpty() || entries.size() > 200)
        throw invalid("완료할 주문을 1~200건 선택해 주세요.");
      var targets = new TreeMap<String, GoodsSurveyFulfillment>();
      for (Object entry : entries) {
        if (!(entry instanceof Map<?, ?> value) || !(value.get("orderNumber") instanceof String number)
            || number.isBlank() || number.length() > 20 || targets.containsKey(number))
          throw invalid("주문번호가 없거나 중복되었습니다.");
        var order = workflow.locked(number);
        var version = new HashMap<String, Object>();
        version.put("version", value.get("version"));
        workflow.version(order, version);
        if (!order.orderStatus().equals("ACTIVE")
            || !order.shipmentStatus().equals("NOT_READY")
            || !Set.of("CONFIRMED", "NOT_REQUIRED").contains(order.paymentStatus())
            || !Set.of(GoodsOrderStatus.PAYMENT_COMPLETED, GoodsOrderStatus.IN_PRODUCTION,
                GoodsOrderStatus.LEGACY_FREE).contains(order.getStatus()))
          throw new WorkflowException(409, "ORDER_NOT_COMPLETABLE",
              number + ": 결제가 확인된 미발송 진행 주문만 관리자 완료로 처리할 수 있습니다.");
        targets.put(number, order);
      }
      var records = new ArrayList<Map<String, Object>>();
      for (var order : targets.values()) {
        var before = order.administrativeState();
        order.completeAdministratively();
        workflow.touch(order);
        workflow.audit(order.getOrderNumber(), "ADMIN_COMPLETE", encode(before),
            encode(order.administrativeState()), reason);
        orderHistory.recordManualChange(order.getResponseId(), before.status(), order.getStatus(),
            String.valueOf(access.current().getId()), reason);
        records.add(Map.of("orderNumber", order.getOrderNumber(), "version", order.getVersion(),
            "before", before, "after", order.administrativeState()));
      }
      return Map.of("completed", records.size(), "operationId", key, "orders", records);
    });
  }

  public Map<String, Object> restore(String number, String key, Map<String, Object> body) {
    owner();
    return workflow.command("restore-administrative-completion:" + number, key, body, () -> {
      owner();
      String reason = reason(body);
      var order = workflow.locked(number);
      workflow.version(order, body);
      if (order.getStatus() != GoodsOrderStatus.ADMIN_COMPLETED)
        throw new WorkflowException(409, "ORDER_NOT_ADMIN_COMPLETED", "관리자 완료 주문만 복구할 수 있습니다.");
      var record = audits.findByResourceOrderByIdAsc(number).stream()
          .filter(event -> event.getAction().equals("ADMIN_COMPLETE"))
          .reduce((previous, current) -> current)
          .orElseThrow(() -> new WorkflowException(409, "COMPLETION_RECORD_MISSING", "변경 전 기록이 없습니다."));
      try {
        var before = order.administrativeState();
        order.restoreAdministrativeState(json.readValue(record.getBeforeValue(),
            GoodsSurveyFulfillment.AdministrativeState.class));
        workflow.touch(order);
        workflow.audit(number, "RESTORE_ADMIN_COMPLETE", encode(before),
            encode(order.administrativeState()), reason);
        orderHistory.recordManualChange(order.getResponseId(), before.status(), order.getStatus(),
            String.valueOf(access.current().getId()), reason);
        return workflow.view(order);
      } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    });
  }
}
