package com.pawever.backend.workflow;

import static com.pawever.backend.admin.entity.PermissionKey.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawever.backend.goodssurvey.entity.*;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ShipmentExportService {
  private final WorkflowService workflow;
  private final StaffPermissions access;
  private final GoodsSurveyFulfillmentRepository orders;
  private final ShipmentExportBatchRepository batches;
  private final ShipmentExportItemRepository items;
  private final ShipmentWorkbook workbook;
  private final ProductionSettlementService compensation;
  private final Clock clock;
  private final com.pawever.backend.goodssurvey.config.GoodsSurveyProperties properties;
  private final ObjectMapper json = new ObjectMapper();

  public List<Map<String, Object>> candidates() {
    access.require(PACK_AND_EXPORT_SHIPMENTS);
    var result =
        orders
            .findByProductionStageAndDeliveryMethodOrderByIdAsc(
                ProductionStage.PACKING, GoodsDeliveryMethod.SHIPPING)
            .stream()
            .filter(o -> workflow.active(o) && access.canRead(o.getOrderNumber()))
            .map(this::candidate)
            .toList();
    result.forEach(
        row ->
            workflow.audit(
                (String) row.get("orderNumber"),
                "VIEW_PACKING_CUSTOMER_DATA",
                null,
                null,
                "포장 정보 조회"));
    return result;
  }

  public List<Map<String, Object>> pickupCandidates() {
    access.require(COMPLETE_PICKUP);
    return orders
        .findByProductionStageAndDeliveryMethodOrderByIdAsc(
            ProductionStage.PACKING, GoodsDeliveryMethod.PICKUP)
        .stream()
        .filter(o -> workflow.active(o) && access.canRead(o.getOrderNumber()))
        .map(
            o ->
                Map.<String, Object>of(
                    "orderNumber", o.getOrderNumber(),
                    "version", o.getVersion(),
                    "petName", o.getPetName(),
                    "guardianName", o.getGuardianName(),
                    "productionStage", o.getProductionStage().name(),
                    "shipmentStatus", o.shipmentStatus(),
                    "blockingIssues", pickupProblems(o)))
        .toList();
  }

  Map<String, Object> candidate(GoodsSurveyFulfillment o) {
    var m = new LinkedHashMap<String, Object>();
    m.put("orderNumber", o.getOrderNumber());
    m.put("version", o.getVersion());
    m.put("petName", o.getPetName());
    m.put("guardianName", o.getGuardianName());
    m.put("phone", o.getPhone());
    m.put("postalCode", o.getPostalCode());
    m.put("address", o.getAddress());
    m.put("addressDetail", o.getAddressDetail());
    m.put("productionStage", o.getProductionStage());
    m.put("shipmentStatus", o.shipmentStatus());
    m.put("blockingIssues", problems(o));
    return m;
  }

  List<String> problems(GoodsSurveyFulfillment o) {
    var p = new ArrayList<String>();
    if (!workflow.active(o)
        || !workflow.paid(o)
        || o.getProductionStage() != ProductionStage.PACKING
        || o.getDeliveryMethod() != GoodsDeliveryMethod.SHIPPING)
      p.add("검수를 통과한 배송 주문만 포장할 수 있습니다.");
    var task = workflow.currentTask(o.getOrderNumber());
    if (task == null || task.getStage() != ProductionStage.PACKING) p.add("포장 작업을 확인해 주세요.");
    if (!workflow.codes(o.getOrderNumber()).isEmpty()) p.add("먼저 주문의 차단 이슈를 해결해 주세요.");
    if (items.existsByOrderNumber(o.getOrderNumber())) p.add("이미 내보낸 주문입니다. 기존 배치 파일을 다시 받아 주세요.");
    if (!value(o.getPostalCode()).matches("[0-9]{5}")) p.add("우편번호는 5자리 숫자가 필요합니다.");
    if (!value(o.getPhone()).matches("[0-9\\s().+\\-]+") || !phone(o).matches("010[0-9]{8}"))
      p.add("전화번호는 010으로 시작하는 11자리 숫자가 필요합니다.");
    if (value(o.getGuardianName()).isBlank()) p.add("보호자 이름을 확인해 주세요.");
    if (value(o.getPetName()).isBlank()) p.add("반려동물 이름을 확인해 주세요.");
    if (value(o.getAddress()).isBlank()) p.add("기본 주소를 확인해 주세요.");
    if (exportValues(o).stream()
        .anyMatch(
            s ->
                s.length() > 2000
                    || s.codePoints().anyMatch(c -> c < 32 && c != 9 && c != 10 && c != 13)))
      p.add("배송 정보에 사용할 수 없는 문자가 있습니다.");
    return p;
  }

  List<String> pickupProblems(GoodsSurveyFulfillment o) {
    var p = new ArrayList<String>();
    if (!workflow.active(o)
        || !workflow.paid(o)
        || o.getProductionStage() != ProductionStage.PACKING
        || o.getDeliveryMethod() != GoodsDeliveryMethod.PICKUP)
      p.add("검수를 통과한 직접 수령 주문만 포장할 수 있습니다.");
    var task = workflow.currentTask(o.getOrderNumber());
    if (task == null || task.getStage() != ProductionStage.PACKING)
      p.add("포장 작업을 확인해 주세요.");
    if (!workflow.codes(o.getOrderNumber()).isEmpty())
      p.add("먼저 주문의 차단 이슈를 해결해 주세요.");
    return p;
  }

  static String value(String s) {
    return s == null ? "" : s;
  }

  String phone(GoodsSurveyFulfillment o) {
    return value(o.getPhone()).replaceAll("[^0-9]", "");
  }

  List<String> exportValues(GoodsSurveyFulfillment o) {
    return List.of(
        value(o.getGuardianName()),
        value(o.getPostalCode()),
        value(o.getAddress()),
        value(o.getAddressDetail()),
        phone(o),
        value(o.getPetName()));
  }

  @SuppressWarnings("unchecked")
  List<Map<String, Object>> selection(Map<String, Object> b) {
    if (!(b.get("orders") instanceof List<?> rows) || rows.isEmpty() || rows.size() > 100)
      throw new WorkflowException(400, "INVALID_INPUT", "주문을 1~100건 선택해 주세요.");
    var seen = new HashSet<String>();
    for (var row : rows) {
      if (!(row instanceof Map<?, ?> m)
          || !(m.get("orderNumber") instanceof String n)
          || n.isBlank()
          || n.length() > 20
          || !seen.add(n))
        throw new WorkflowException(400, "INVALID_INPUT", "중복 없는 주문번호를 확인해 주세요.");
    }
    return (List<Map<String, Object>>) (List<?>) rows;
  }

  public Map<String, Object> create(String key, Map<String, Object> b) {
    access.require(PACK_AND_EXPORT_SHIPMENTS);
    var requested = selection(b);
    requested.forEach(r -> access.read((String) r.get("orderNumber")));
    var result =
        workflow.command(
            "shipment-export",
            key,
            b,
            () -> {
              access.require(PACK_AND_EXPORT_SHIPMENTS);
              var locked = new LinkedHashMap<String, GoodsSurveyFulfillment>();
              requested.stream()
                  .map(r -> (String) r.get("orderNumber"))
                  .sorted()
                  .forEach(
                      n -> {
                        access.read(n);
                        locked.put(n, workflow.locked(n));
                      });
              var errors = new ArrayList<Map<String, Object>>();
              for (var row : requested) {
                var o = locked.get(row.get("orderNumber"));
                workflow.version(o, row);
                var p = problems(o);
                if (!p.isEmpty())
                  errors.add(Map.of("orderNumber", o.getOrderNumber(), "blockingIssues", p));
              }
              if (!errors.isEmpty())
                throw new WorkflowException(
                    422, "SHIPMENT_NOT_READY", "선택 주문의 배송 정보를 확인해 주세요. 포장 상태는 바뀌지 않았습니다.", errors);
              var selected = requested.stream().map(r -> locked.get(r.get("orderNumber"))).toList();
              byte[] bytes = workbook.create(selected.stream().map(this::exportValues).toList());
              var at = clock.instant();
              var batch =
                  batches.saveAndFlush(
                      ShipmentExportBatch.create(
                          access.current().getId(), at, selected.size(), bytes));
              for (int index = 0; index < selected.size(); index++) {
                var o = selected.get(index);
                var raw = exportValues(o);
                var normalized = raw.stream().map(s -> s.strip().replaceAll("\\s+", " ")).toList();
                try {
                  String hash =
                      HexFormat.of()
                          .formatHex(
                              MessageDigest.getInstance("SHA-256")
                                  .digest(json.writeValueAsBytes(normalized)));
                  items.saveAndFlush(
                      ShipmentExportItem.create(
                          batch.getId(),
                          o.getId(),
                          o.getOrderNumber(),
                          index + 1,
                          json.writeValueAsString(
                              Map.of("exported", raw, "normalized", normalized)),
                          hash));
                } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) {
                  throw new IllegalStateException(e);
                }
                var task = workflow.currentTask(o.getOrderNumber());
                task.assign(access.current().getId());
                task.complete(at);
                o.completePackingForPostOffice();
                workflow.touch(o);
                workflow.audit(
                    o.getOrderNumber(),
                    "PACK_AND_EXPORT_SHIPMENTS",
                    "PACKING",
                    "AWAITING_POST_OFFICE_RESULT",
                    "배치 " + batch.getId());
              }
              return summary(batch);
            });
    access.require(PACK_AND_EXPORT_SHIPMENTS);
    requested.forEach(r -> access.read((String) r.get("orderNumber")));
    return result;
  }

  public Map<String, Object> completePickupPacking(String key, Map<String, Object> body) {
    access.require(COMPLETE_PICKUP);
    var requested = selection(body);
    requested.forEach(r -> access.read((String) r.get("orderNumber")));
    return workflow.command(
        "pickup-pack",
        key,
        body,
        () -> {
          access.require(COMPLETE_PICKUP);
          var locked = new LinkedHashMap<String, GoodsSurveyFulfillment>();
          requested.stream()
              .map(r -> (String) r.get("orderNumber"))
              .sorted()
              .forEach(
                  n -> {
                    access.read(n);
                    locked.put(n, workflow.locked(n));
                  });
          var errors = new ArrayList<Map<String, Object>>();
          for (var row : requested) {
            var order = locked.get(row.get("orderNumber"));
            workflow.version(order, row);
            var problems = pickupProblems(order);
            if (!problems.isEmpty())
              errors.add(
                  Map.of(
                      "orderNumber", order.getOrderNumber(),
                      "blockingIssues", problems));
          }
          if (!errors.isEmpty())
            throw new WorkflowException(
                422,
                "PICKUP_NOT_READY",
                "선택 주문의 직접 수령 포장 상태를 확인해 주세요.",
                errors);

          var at = clock.instant();
          var settlementResults = new LinkedHashMap<String, String>();
          for (var order : requested.stream().map(r -> locked.get(r.get("orderNumber"))).toList()) {
            var task = workflow.currentTask(order.getOrderNumber());
            task.assign(access.current().getId());
            task.complete(at);
            order.completePackingForPickup();
            workflow.touch(order);
            String settlement = compensation.recordAtFulfillment(order.getOrderNumber());
            settlementResults.put(order.getOrderNumber(), settlement);
            workflow.audit(
                order.getOrderNumber(),
                "COMPLETE_PICKUP_PACKING",
                "PACKING",
                "READY_FOR_PICKUP",
                "정산 " + settlement);
          }
          return Map.of(
              "completed", requested.size(),
              "orderNumbers", requested.stream().map(r -> r.get("orderNumber")).toList(),
              "settlements", settlementResults);
        });
  }

  public List<Map<String, Object>> list() {
    access.require(PACK_AND_EXPORT_SHIPMENTS);
    return batches.findAllByOrderByIdDesc().stream()
        .filter(b -> members(b).stream().allMatch(i -> access.canRead(i.getOrderNumber())))
        .map(this::summary)
        .toList();
  }

  List<ShipmentExportItem> members(ShipmentExportBatch b) {
    return items.findByBatchIdOrderByRowNumberAsc(b.getId());
  }

  Map<String, Object> summary(ShipmentExportBatch b) {
    return Map.of(
        "id",
        b.getId(),
        "exportedAt",
        b.getExportedAt().toString(),
        "orderCount",
        b.getOrderCount(),
        "orderNumbers",
        members(b).stream().map(ShipmentExportItem::getOrderNumber).toList(),
        "downloadable",
        b.getFileBase64() != null
            && members(b).stream().noneMatch(i -> expired(i, clock.instant())));
  }

  public byte[] download(Long id) {
    access.require(PACK_AND_EXPORT_SHIPMENTS);
    var b =
        batches
            .findById(id)
            .orElseThrow(() -> new WorkflowException(404, "NOT_FOUND", "배치를 찾을 수 없습니다."));
    var rows = members(b);
    rows.forEach(i -> access.read(i.getOrderNumber()));
    if (b.getFileBase64() == null || rows.stream().anyMatch(i -> expired(i, clock.instant())))
      throw new WorkflowException(
          410, "EXPORT_UNAVAILABLE", "보관 기간이 끝났거나 취소된 주문이 포함되어 파일을 제공할 수 없습니다.");
    workflow.audit("shipment-export:" + id, "DOWNLOAD_SHIPMENT_EXPORT", null, null, "파일 다운로드");
    return Base64.getDecoder().decode(b.getFileBase64());
  }

  boolean expired(ShipmentExportItem item, Instant now) {
    var o = orders.findById(item.getOrderId()).orElse(null);
    return o == null
        || Set.of("CANCELED", "EXPIRED").contains(o.orderStatus())
        || (o.getDeleteAfter() != null && !o.getDeleteAfter().isAfter(now))
        || (o.getDeliveryCompletedAt() != null
            && !com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment
                .deliveryRetentionExpiresAt(
                    o.getDeliveryCompletedAt(), properties.getPersonalDataRetentionMonths())
                .isAfter(now));
  }

  public void purge(Instant now) {
    for (var item : items.findBySnapshotJsonIsNotNull()) {
      orders.lockByOrderNumber(item.getOrderNumber());
      if (expired(item, now)) {
        item.purgeSnapshot();
        batches.findById(item.getBatchId()).ifPresent(ShipmentExportBatch::purgeFile);
      }
    }
  }
}
