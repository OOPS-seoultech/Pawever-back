package com.pawever.backend.workflow;

import static com.pawever.backend.admin.entity.PermissionKey.*;

import com.pawever.backend.admin.entity.WorkRole;
import com.pawever.backend.admin.repository.AdminAccountRepository;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class ProductionFinishingService {
  private final WorkflowService workflow;
  private final PrintBatchService plates;
  private final StaffPermissions access;
  private final ProductionTaskRepository tasks;
  private final PrintBatchRepository batches;
  private final PrintBatchItemRepository items;
  private final PrintRunResultRepository results;
  private final PrintBatchObservationRepository observations;
  private final FinishingRecordRepository records;
  private final WorkflowSettingsRepository settings;
  private final ProductionCompensationSettingsRepository compensation;
  private final ProductionPaidWorkerRepository paidWorkers;
  private final ProductionSettlementRepository settlements;
  private final AdminAccountRepository accounts;
  private final jakarta.persistence.EntityManager em;
  private final Clock clock;

  private void ownBatch(PrintBatch b) {
    access.require(MANAGE_PRINT_BATCH);
    if (!Objects.equals(b.getPrintingAssigneeId(), access.current().getId())
        || access.eligible(access.current().getId(), WorkRole.PRINT_FINISHING) == null)
      throw new WorkflowException(403, "FORBIDDEN", "배정된 출력 담당자만 처리할 수 있습니다.");
  }

  public Map<String, Object> cancelQueued(Long id, String key, Map<String, Object> input) {
    access.require(ASSIGN_WORK);
    plates.read(id);
    return workflow.command(
        "cancel-queued-print:" + id,
        key,
        input,
        () -> {
          access.require(ASSIGN_WORK);
          var b = plates.fresh(id);
          plates.read(id);
          plates.version(b, input);
          if (!b.getStatus().equals("CONFIRMED")) throw plates.bad("출력 시작 전 대기 플레이트만 해제할 수 있습니다.");
          String note = plates.text(input, "note", 1000);
          if (note.isBlank()) throw plates.bad("재구성 사유를 입력해 주세요.");
          var locked = members(b, input);
          for (var i : items.findByBatchIdOrderByIdAsc(id)) {
            var o = locked.get(i.getOrderNumber());
            var t = batchTask(i, ProductionStage.PRINT_QUEUE);
            if (workflow.active(o) && workflow.paid(o))
              advance(
                  o,
                  t,
                  ProductionStage.PLATE_PREPARATION,
                  designer(o.getOrderNumber()),
                  nextAttempt(o.getOrderNumber()));
            else {
              t.complete(clock.instant());
              workflow.touch(o);
            }
            workflow.audit(
                o.getOrderNumber(),
                "CANCEL_QUEUED_PRINT",
                "PRINT_QUEUE",
                o.getProductionStage().name(),
                note);
          }
          b.cancel(clock.instant());
          batches.saveAndFlush(b);
          workflow.audit("plate:" + id, "CANCEL_QUEUED_PRINT", "CONFIRMED", "CANCELED", note);
          return plates.view(b);
        });
  }

  private Map<String, GoodsSurveyFulfillment> members(PrintBatch b, Map<String, Object> input) {
    var selected = plates.selected(input);
    var members = items.findByBatchIdOrderByIdAsc(b.getId());
    if (!selected
        .keySet()
        .equals(new TreeSet<>(members.stream().map(PrintBatchItem::getOrderNumber).toList())))
      throw plates.bad("포함 주문 전체의 최신 결과를 확인해 주세요.");
    var locked = plates.lock(selected.keySet());
    selected.forEach((n, body) -> workflow.version(locked.get(n), body));
    return locked;
  }

  private ProductionTask batchTask(PrintBatchItem item, ProductionStage stage) {
    var plateTask = tasks.findById(item.getPlateTaskId()).orElseThrow();
    var t = workflow.currentTask(item.getOrderNumber());
    if (t != null) em.refresh(t);
    if (t == null || t.getStage() != stage || t.getAttempt() != plateTask.getAttempt())
      throw plates.bad("포함 주문의 작업 단계나 시도가 변경됐습니다.");
    return t;
  }

  private void active(GoodsSurveyFulfillment o) {
    if (!workflow.active(o) || !workflow.paid(o)) throw plates.bad("진행 가능한 결제 확인 주문인지 확인해 주세요.");
  }

  private void advance(
      GoodsSurveyFulfillment o,
      ProductionTask previous,
      ProductionStage next,
      Long assignee,
      int attempt) {
    previous.complete(clock.instant());
    tasks.saveAndFlush(ProductionTask.create(o.getOrderNumber(), next, assignee, attempt));
    o.moveProduction(next == ProductionStage.MODELING ? ProductionStage.MODELING_QUEUE : next);
    workflow.issue(
        o.getOrderNumber(), "UNASSIGNED", assignee == null && next != ProductionStage.PACKING);
    workflow.touch(o);
  }

  private int nextAttempt(String number) {
    return tasks.findByOrderNumberOrderByIdAsc(number).stream()
            .mapToInt(ProductionTask::getAttempt)
            .max()
            .orElse(0)
        + 1;
  }

  private Long designer(String number) {
    var history = tasks.findByOrderNumberOrderByIdAsc(number);
    Long former =
        history.stream()
            .filter(t -> t.getStage() == ProductionStage.PLATE_PREPARATION)
            .reduce((a, b) -> b)
            .map(ProductionTask::getAssigneeId)
            .orElse(null);
    Long id = access.eligible(former, WorkRole.DESIGN_QC);
    return id != null
        ? id
        : access.eligible(
            settings.findById(1L).map(WorkflowSettings::getReview).orElse(null),
            WorkRole.DESIGN_QC);
  }

  public Map<String, Object> start(Long id, String key, Map<String, Object> input) {
    ownBatch(plates.read(id));
    return workflow.command(
        "print-start:" + id,
        key,
        input,
        () -> {
          var b = plates.fresh(id);
          ownBatch(b);
          plates.version(b, input);
          if (!b.getStatus().equals("CONFIRMED")) throw plates.bad("확정된 출력 대기 플레이트만 시작할 수 있습니다.");
          var locked = members(b, input);
          plates.validateLayout(b, true);
          if (b.getArtifactId() == null) throw plates.bad("현재 출력 파일이 없습니다.");
          for (var i : items.findByBatchIdOrderByIdAsc(id)) {
            var o = locked.get(i.getOrderNumber());
            active(o);
            var t = batchTask(i, ProductionStage.PRINT_QUEUE);
            if (!Objects.equals(t.getAssigneeId(), b.getPrintingAssigneeId())
                || !workflow.actionableBlockers(t, workflow.codes(o.getOrderNumber())).isEmpty())
              throw plates.bad("포함 주문의 담당자와 차단 문제를 확인해 주세요.");
            advance(o, t, ProductionStage.PRINTING, b.getPrintingAssigneeId(), t.getAttempt());
            workflow.currentTask(o.getOrderNumber()).start(clock.instant());
            workflow.audit(
                o.getOrderNumber(), "START_PRINT_BATCH", "PRINT_QUEUE", "PRINTING", "PB-" + id);
          }
          b.start(clock.instant());
          batches.saveAndFlush(b);
          workflow.audit("plate:" + id, "START_PRINT_BATCH", "CONFIRMED", "PRINTING", null);
          return plates.view(b);
        });
  }

  public Map<String, Object> observe(Long id, String key, Map<String, Object> input) {
    ownBatch(plates.read(id));
    return workflow.command(
        "print-observe:" + id,
        key,
        input,
        () -> {
          var b = plates.fresh(id);
          ownBatch(b);
          plates.version(b, input);
          if (!b.getStatus().equals("PRINTING")) throw plates.bad("출력 중인 플레이트에 기록해 주세요.");
          String note = plates.text(input, "note", 1000);
          BigDecimal grams = null;
          if (input.get("purgeGrams") != null) {
            try {
              grams = new BigDecimal(input.get("purgeGrams").toString());
            } catch (NumberFormatException e) {
              throw plates.bad("퍼지 무게를 확인해 주세요.");
            }
            if (grams.signum() < 0
                || grams.compareTo(new BigDecimal("100000")) > 0
                || grams.stripTrailingZeros().scale() > 2)
              throw plates.bad("퍼지 무게는 0–100000g, 소수 둘째 자리까지 입력해 주세요.");
          }
          var problemRows = plates.entries(input, "issues", 0, 50);
          if (note.isBlank() && grams == null && problemRows.isEmpty())
            throw plates.bad("중간 확인 내용을 입력해 주세요.");
          var all =
              plates.lock(
                  items.findByBatchIdOrderByIdAsc(id).stream()
                      .map(PrintBatchItem::getOrderNumber)
                      .toList());
          var seen = new HashSet<String>();
          var clean = new ArrayList<Map<String, Object>>();
          for (var row : problemRows) {
            String n = plates.text(row, "orderNumber", 20), reason = plates.text(row, "note", 1000);
            if (!all.containsKey(n) || !seen.add(n) || reason.isBlank())
              throw plates.bad("이상이 있는 포함 주문과 사유를 확인해 주세요.");
            active(all.get(n));
            workflow.issue(n, "PRINT_ANOMALY", true);
            workflow.touch(all.get(n));
            clean.add(plates.map("orderNumber", n, "note", reason));
          }
          String json;
          try {
            json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(clean);
          } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
          }
          observations.saveAndFlush(
              PrintBatchObservation.of(
                  id, access.current().getId(), note, grams, json, clock.instant()));
          b.touch(clock.instant());
          batches.saveAndFlush(b);
          workflow.audit("plate:" + id, "RECORD_PRINT_OBSERVATION", null, json, note);
          return plates.view(b);
        });
  }

  public Map<String, Object> finish(Long id, String key, Map<String, Object> input) {
    ownBatch(plates.read(id));
    return workflow.command(
        "print-finish:" + id,
        key,
        input,
        () -> {
          var b = plates.fresh(id);
          ownBatch(b);
          plates.version(b, input);
          if (!b.getStatus().equals("PRINTING")) throw plates.bad("출력 중인 플레이트만 완료할 수 있습니다.");
          var locked = members(b, input);
          var selected = plates.selected(input);
          for (var i : items.findByBatchIdOrderByIdAsc(id)) {
            var o = locked.get(i.getOrderNumber());
            var t = batchTask(i, ProductionStage.PRINTING);
            String result = plates.text(selected.get(o.getOrderNumber()), "result", 20),
                note = plates.text(selected.get(o.getOrderNumber()), "note", 1000);
            if (!Set.of("SUCCESS", "FAILED").contains(result)
                || result.equals("FAILED") && note.isBlank())
              throw plates.bad("주문별 성공·실패와 실패 사유를 확인해 주세요.");
            if (!Objects.equals(t.getAssigneeId(), b.getPrintingAssigneeId()))
              throw plates.bad("포함 주문의 출력 담당자가 변경됐습니다.");
            if (!workflow.active(o) || !workflow.paid(o)) {
              result = "SKIPPED";
              note = "주문 취소·결제·배송 상태 변경으로 제작 인계 제외";
              t.complete(clock.instant());
              workflow.touch(o);
            } else if (result.equals("SUCCESS")) {
              advance(
                  o, t, ProductionStage.POST_PROCESSING, b.getPrintingAssigneeId(), t.getAttempt());
              workflow.issue(o.getOrderNumber(), "PRINT_FAILED", false);
            } else {
              advance(
                  o,
                  t,
                  ProductionStage.PLATE_PREPARATION,
                  designer(o.getOrderNumber()),
                  nextAttempt(o.getOrderNumber()));
              workflow.issue(o.getOrderNumber(), "PRINT_FAILED", true);
            }
            workflow.issue(o.getOrderNumber(), "PRINT_ANOMALY", false);
            results.saveAndFlush(
                PrintRunResult.of(id, t, result, note, access.current().getId(), clock.instant()));
            workflow.audit(o.getOrderNumber(), "FINISH_PRINT_BATCH", "PRINTING", result, note);
          }
          b.finish(clock.instant());
          batches.saveAndFlush(b);
          workflow.audit("plate:" + id, "FINISH_PRINT_BATCH", "PRINTING", "FINISHED", null);
          return plates.view(b);
        });
  }

  private ProductionTask ownTask(Long id) {
    var t =
        tasks
            .findById(id)
            .orElseThrow(() -> new WorkflowException(404, "NOT_FOUND", "작업을 찾을 수 없습니다."));
    access.read(t.getOrderNumber());
    access.require(COMPLETE_POST_PROCESSING);
    if (!Objects.equals(t.getAssigneeId(), access.current().getId())
        || access.eligible(access.current().getId(), WorkRole.PRINT_FINISHING) == null)
      throw new WorkflowException(403, "FORBIDDEN", "배정된 출력·후가공 담당자만 처리할 수 있습니다.");
    return t;
  }

  private GoodsSurveyFulfillment taskOrder(
      ProductionTask t, ProductionStage stage, Map<String, Object> input) {
    var o = workflow.locked(t.getOrderNumber());
    workflow.version(o, input);
    active(o);
    var current = workflow.currentTask(o.getOrderNumber());
    if (current == null
        || !current.getId().equals(t.getId())
        || t.getStage() != stage
        || o.getProductionStage() != stage
        || !workflow.actionableBlockers(t, workflow.codes(o.getOrderNumber())).isEmpty())
      throw plates.bad("현재 단계와 차단 문제를 확인해 주세요.");
    return o;
  }

  private String checks(Map<String, Object> input, Set<String> expected) {
    if (!(input.get("checks") instanceof List<?> list)
        || list.size() != expected.size()
        || !new HashSet<>(list).equals(expected)) throw plates.bad("필수 확인 항목을 모두 확인해 주세요.");
    return String.join(",", new TreeSet<>(expected));
  }

  public Map<String, Object> postProcess(Long id, String key, Map<String, Object> input) {
    ownTask(id);
    return workflow.command(
        "post-process:" + id,
        key,
        input,
        () -> {
          var t = ownTask(id);
          em.refresh(t);
          var o = taskOrder(t, ProductionStage.POST_PROCESSING, input);
          String checked = checks(input, Set.of("SUPPORT_REMOVED", "SURFACE_CHECKED"));
          String resin = plates.text(input, "resinCuring", 30),
              note = plates.text(input, "note", 1000);
          if (!Set.of("DONE", "NOT_APPLICABLE").contains(resin))
            throw plates.bad("레진 경화 완료 또는 레진 미사용을 선택해 주세요.");
          records.saveAndFlush(
              FinishingRecord.of(
                  t,
                  "COMPLETED",
                  checked + ",RESIN_" + resin,
                  note,
                  "",
                  "",
                  "NOT_APPLICABLE",
                  access.current().getId(),
                  clock.instant()));
          advance(o, t, ProductionStage.QC, t.getAssigneeId(), t.getAttempt());
          workflow.audit(
              o.getOrderNumber(), "COMPLETE_POST_PROCESSING", "POST_PROCESSING", "QC", note);
          return workflow.view(o);
        });
  }

  public Map<String, Object> qualityCheck(Long id, String key, Map<String, Object> input) {
    ownTask(id);
    return workflow.command(
        "quality-check:" + id,
        key,
        input,
        () -> {
          var t = ownTask(id);
          em.refresh(t);
          var o = taskOrder(t, ProductionStage.QC, input);
          String decision = plates.text(input, "decision", 20),
              note = plates.text(input, "note", 1000),
              checked = "",
              reason = "",
              route = "",
              settlement = "NOT_APPLICABLE";
          if (decision.equals("PASSED")) {
            checked = checks(input, Set.of("SHAPE_COLOR", "SURFACE", "EYES_NOSE"));
            settlement = accrue(t);
            advance(o, t, ProductionStage.PACKING, null, t.getAttempt());
            workflow.issue(o.getOrderNumber(), "QC_FAILED", false);
          } else if (decision.equals("FAILED")) {
            reason = plates.text(input, "reasonCode", 40);
            route = plates.text(input, "reworkStage", 30);
            if (!Set.of("SHAPE", "COLOR", "PRINT_DEFECT", "FINISH_DEFECT").contains(reason)
                || note.isBlank()
                || !Set.of("MODELING", "PLATE_PREPARATION", "POST_PROCESSING").contains(route))
              throw plates.bad("불합격 사유·보정 경로·메모를 입력해 주세요.");
            ProductionStage next = ProductionStage.valueOf(route);
            Long worker = t.getAssigneeId();
            if (next == ProductionStage.PLATE_PREPARATION) worker = designer(o.getOrderNumber());
            if (next == ProductionStage.MODELING) {
              Long former =
                  tasks.findByOrderNumberOrderByIdAsc(o.getOrderNumber()).stream()
                      .filter(x -> x.getStage() == ProductionStage.MODELING)
                      .reduce((a, b) -> b)
                      .map(ProductionTask::getAssigneeId)
                      .orElse(null);
              worker = access.eligible(former, WorkRole.MODELING);
              if (worker == null)
                worker =
                    access.eligible(
                        settings.findById(1L).map(WorkflowSettings::getModeling).orElse(null),
                        WorkRole.MODELING);
            }
            advance(o, t, next, worker, nextAttempt(o.getOrderNumber()));
            workflow.issue(o.getOrderNumber(), "QC_FAILED", true);
          } else throw plates.bad("검수 통과 또는 불합격을 선택해 주세요.");
          records.saveAndFlush(
              FinishingRecord.of(
                  t,
                  decision,
                  checked,
                  note,
                  reason,
                  route,
                  settlement,
                  access.current().getId(),
                  clock.instant()));
          workflow.audit(
              o.getOrderNumber(),
              decision.equals("PASSED") ? "PASS_QUALITY_CHECK" : "FAIL_QUALITY_CHECK",
              "QC",
              o.getProductionStage().name(),
              note);
          return workflow.view(o);
        });
  }

  private String accrue(ProductionTask t) {
    if (settlements.existsByOrderNumber(t.getOrderNumber())) return "ALREADY_RECORDED";
    if (!compensation.findById(1L).map(ProductionCompensationSettings::isEnabled).orElse(false))
      return "DISABLED";
    if (!paidWorkers.existsById(t.getAssigneeId())) return "UNPAID_WORKER";
    settlements.saveAndFlush(ProductionSettlement.of(t, t.getAssigneeId(), clock.instant()));
    return "RECORDED";
  }

  public Map<String, Object> compensation() {
    access.require(MANAGE_OPERATION_SETTINGS);
    var c =
        compensation
            .findById(1L)
            .orElseGet(() -> compensation.saveAndFlush(new ProductionCompensationSettings()));
    return plates.map(
        "version",
        c.getVersion(),
        "enabled",
        c.isEnabled(),
        "amountKrw",
        3000,
        "paidWorkerIds",
        paidWorkers.findAll().stream().map(ProductionPaidWorker::getAccountId).sorted().toList());
  }

  public Map<String, Object> configureCompensation(String key, Map<String, Object> input) {
    access.require(MANAGE_OPERATION_SETTINGS);
    return workflow.command(
        "production-compensation",
        key,
        input,
        () -> {
          access.require(MANAGE_OPERATION_SETTINGS);
          compensation();
          var c = compensation.findById(1L).orElseThrow();
          em.refresh(c);
          if (c.getVersion() != plates.number(input, "version"))
            throw new WorkflowException(409, "VERSION_CONFLICT", "정산 설정이 변경됐습니다. 다시 확인해 주세요.");
          if (!(input.get("enabled") instanceof Boolean enabled)
              || !(input.get("paidWorkerIds") instanceof List<?> ids)
              || ids.size() > 100) throw plates.bad("정산 설정을 확인해 주세요.");
          var selected = new TreeSet<Long>();
          for (Object raw : ids) {
            long id = plates.number(plates.map("id", raw), "id");
            if (!selected.add(id) || access.eligible(id, WorkRole.PRINT_FINISHING) == null)
              throw plates.bad("활성 출력·후가공 담당자를 선택해 주세요.");
          }
          String before = compensation().toString();
          paidWorkers.deleteAll();
          paidWorkers.flush();
          paidWorkers.saveAllAndFlush(selected.stream().map(ProductionPaidWorker::of).toList());
          c.change(enabled);
          compensation.saveAndFlush(c);
          workflow.audit(
              "production-compensation",
              "CONFIGURE_COMPENSATION",
              before,
              compensation().toString(),
              null);
          return compensation();
        });
  }

  public List<Map<String, Object>> settlements() {
    access.require(MANAGE_OPERATION_SETTINGS);
    access.require(VIEW_ORDER_BASIC);
    return settlements.findAllByOrderByIdDesc().stream()
        .filter(r -> access.canRead(r.getOrderNumber()))
        .map(
            r ->
                plates.map(
                    "id",
                    r.getId(),
                    "orderNumber",
                    r.getOrderNumber(),
                    "workerName",
                    accounts.findById(r.getWorkerId()).map(a -> a.getName()).orElse("이전 담당자"),
                    "amountKrw",
                    r.getAmountKrw(),
                    "createdAt",
                    r.getCreatedAt().toString(),
                    "status",
                    "RECORDED"))
        .toList();
  }
}
