package com.pawever.backend.workflow;

import static com.pawever.backend.admin.entity.PermissionKey.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawever.backend.admin.entity.WorkRole;
import com.pawever.backend.admin.repository.AdminAccountRepository;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.service.GoodsSurveyPhotoStorage;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class PrintBatchService {
  private final WorkflowService workflow;
  private final StaffPermissions access;
  private final PrintBatchRepository batches;
  private final PrintBatchItemRepository items;
  private final PrintBatchSlotRepository slots;
  private final PrintBatchArtifactRepository files;
  private final ProductionTaskRepository tasks;
  private final OrderFilamentMappingRepository mappings;
  private final FilamentRepository filaments;
  private final GoodsSurveyFulfillmentRepository orders;
  private final AdminAccountRepository accounts;
  private final WorkflowSettingsRepository settings;
  private final GoodsSurveyPhotoStorage storage;
  private final jakarta.persistence.EntityManager entityManager;
  private final Clock clock;
  private final ObjectMapper json = new ObjectMapper();

  private Map<String, Object> map(Object... pairs) {
    var m = new LinkedHashMap<String, Object>();
    for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
    return m;
  }

  private WorkflowException bad(String message) {
    return new WorkflowException(400, "INVALID_INPUT", message);
  }

  private WorkflowException missing() {
    return new WorkflowException(404, "NOT_FOUND", "플레이트를 찾을 수 없습니다.");
  }

  private String snapshot(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private String text(Map<String, Object> b, String name, int max) {
    Object value = b.get(name);
    if (value == null) return "";
    if (!(value instanceof String s)
        || s.length() > max
        || s.chars().anyMatch(Character::isISOControl)) throw bad(name + " 값을 확인해 주세요.");
    return s.strip();
  }

  private long number(Map<String, Object> b, String name) {
    if (!(b.get(name) instanceof Number n) || n.longValue() < 0 || n.doubleValue() != n.longValue())
      throw bad(name + " 값을 확인해 주세요.");
    return n.longValue();
  }

  private List<Map<String, Object>> entries(Map<String, Object> b, String field, int min, int max) {
    if (!(b.get(field) instanceof List<?> values) || values.size() < min || values.size() > max)
      throw bad(field + " 항목 수를 확인해 주세요.");
    var result = new ArrayList<Map<String, Object>>();
    for (var v : values) {
      if (!(v instanceof Map<?, ?> raw)) throw bad(field + " 형식을 확인해 주세요.");
      var m = new LinkedHashMap<String, Object>();
      raw.forEach((k, value) -> m.put(String.valueOf(k), value));
      result.add(m);
    }
    return result;
  }

  private PrintBatch batch(Long id) {
    return batches.findById(id).orElseThrow(this::missing);
  }

  private PrintBatch fresh(Long id) {
    var b = batch(id);
    entityManager.refresh(b);
    return b;
  }

  private void requireData() {
    access.require(MANAGE_PRINT_BATCH);
  }

  private void creator() {
    requireData();
    if (access.eligible(access.current().getId(), WorkRole.DESIGN_QC) == null)
      throw new WorkflowException(403, "FORBIDDEN", "검수·색상 담당자가 플레이트를 구성할 수 있습니다.");
  }

  private boolean readable(PrintBatch b) {
    if (b.getStatus().equals("CANCELED"))
      return Objects.equals(b.getCreatorId(), access.current().getId())
          || access.has(VIEW_ALL_ORDERS);
    var members = items.findByBatchIdOrderByIdAsc(b.getId());
    return !members.isEmpty() && members.stream().allMatch(i -> access.canRead(i.getOrderNumber()));
  }

  private PrintBatch read(Long id) {
    requireData();
    var b = batch(id);
    if (!readable(b)) throw missing();
    return b;
  }

  private void edit(PrintBatch b) {
    creator();
    if (!b.getCreatorId().equals(access.current().getId()))
      throw new WorkflowException(403, "FORBIDDEN", "플레이트 구성 담당자만 변경할 수 있습니다.");
    if (!readable(b)) throw missing();
  }

  private void draft(PrintBatch b) {
    if (!b.getStatus().equals("DRAFT")) throw bad("임시 플레이트만 변경할 수 있습니다.");
  }

  private void version(PrintBatch b, Map<String, Object> input) {
    if (b.getVersion() != number(input, "version"))
      throw new WorkflowException(
          409, "VERSION_CONFLICT", "플레이트가 변경됐습니다. 최신 내용을 확인해 주세요.", view(b));
  }

  private Map<String, GoodsSurveyFulfillment> lock(Collection<String> numbers) {
    var result = new LinkedHashMap<String, GoodsSurveyFulfillment>();
    for (String n : new TreeSet<>(numbers)) result.put(n, workflow.locked(n));
    return result;
  }

  private Map<String, Map<String, Object>> selected(Map<String, Object> input) {
    var result = new TreeMap<String, Map<String, Object>>();
    for (var item : entries(input, "orders", 1, 50)) {
      String n = text(item, "orderNumber", 20);
      if (n.isBlank() || result.put(n, item) != null) throw bad("주문이 비어 있거나 중복됐습니다.");
      access.read(n);
    }
    return result;
  }

  private ProductionTask eligible(GoodsSurveyFulfillment o, Long batchId) {
    var t = workflow.currentTask(o.getOrderNumber());
    if (t != null) entityManager.refresh(t);
    if (!workflow.active(o)
        || !workflow.paid(o)
        || o.getProductionStage() != ProductionStage.PLATE_PREPARATION
        || t == null
        || t.getStage() != ProductionStage.PLATE_PREPARATION
        || !t.getStatus().equals("WAITING")) throw bad("색상 지정이 완료된 플레이트 준비 주문만 선택해 주세요.");
    if (!Objects.equals(t.getAssigneeId(), access.current().getId()))
      throw new WorkflowException(403, "FORBIDDEN", "배정된 주문만 플레이트에 포함할 수 있습니다.");
    if (!workflow.codes(o.getOrderNumber()).isEmpty()) throw bad("주문의 차단 문제를 먼저 해결해 주세요.");
    var reservation = items.findByPlateTaskId(t.getId()).orElse(null);
    if (reservation != null && !reservation.getBatchId().equals(batchId))
      throw new WorkflowException(409, "BATCH_CONFLICT", "이미 다른 플레이트에 포함된 주문입니다.");
    return t;
  }

  private Long mappingTask(ProductionTask plate) {
    return tasks.findByOrderNumberOrderByIdAsc(plate.getOrderNumber()).stream()
        .filter(
            t ->
                t.getStage() == ProductionStage.COLOR_MAPPING
                    && t.getStatus().equals("COMPLETED")
                    && t.getAttempt() == plate.getAttempt()
                    && t.getId() < plate.getId())
        .reduce((a, b) -> b)
        .map(ProductionTask::getId)
        .orElseThrow(() -> bad("확정된 부위별 필라멘트가 없습니다."));
  }

  private List<OrderFilamentMapping> materialRows(List<PrintBatchItem> members) {
    var result = new ArrayList<OrderFilamentMapping>();
    for (var i : members) {
      var rows = mappings.findByTaskIdOrderByIdAsc(i.getMappingTaskId());
      if (rows.isEmpty() || rows.stream().anyMatch(m -> m.getCompletedAt() == null))
        throw bad("확정된 부위별 필라멘트가 없습니다.");
      result.addAll(rows);
    }
    return result;
  }

  private Map<Long, OrderFilamentMapping> materials(List<PrintBatchItem> members) {
    var result = new LinkedHashMap<Long, OrderFilamentMapping>();
    for (var m : materialRows(members)) result.putIfAbsent(m.getFilamentId(), m);
    return result;
  }

  private void usable(Long id) {
    var f = filaments.findById(id).orElseThrow(() -> bad("등록된 필라멘트를 선택해 주세요."));
    entityManager.refresh(f);
    if (!f.isActive()) throw bad("사용 중지된 필라멘트가 포함돼 있습니다.");
  }

  private void validateLayout(PrintBatch b, boolean requireWorker) {
    if (b.getPrinterName().isBlank()) throw bad("프린터 이름을 입력해 주세요.");
    var used = materials(items.findByBatchIdOrderByIdAsc(b.getId()));
    var assigned = slots.findByBatchIdOrderByIdAsc(b.getId());
    if (used.isEmpty()
        || !used.keySet()
            .equals(new HashSet<>(assigned.stream().map(PrintBatchSlot::getFilamentId).toList())))
      throw bad("사용하는 모든 필라멘트의 슬롯을 지정해 주세요.");
    used.keySet().forEach(this::usable);
    if (requireWorker && !printingEligible(b.getPrintingAssigneeId()))
      throw bad("활성 출력 담당자를 선택해 주세요.");
  }

  private boolean printingEligible(Long id) {
    return access.eligible(id, WorkRole.PRINT_FINISHING) != null
        && accounts
            .findById(id)
            .map(a -> access.effective(a).contains(MANAGE_PRINT_BATCH))
            .orElse(false);
  }

  public Map<String, Object> staff() {
    requireData();
    return map(
        "defaultPrinting",
        settings.findById(1L).map(WorkflowSettings::getPrinting).orElse(null),
        "staff",
        accounts.findAllByOrderByCreatedAtAsc().stream()
            .filter(a -> printingEligible(a.getId()))
            .map(a -> map("id", a.getId(), "name", a.getName()))
            .toList());
  }

  public List<Map<String, Object>> candidates() {
    creator();
    return tasks
        .findByAssigneeIdAndStatusNotOrderByIdAsc(access.current().getId(), "COMPLETED")
        .stream()
        .filter(
            t ->
                t.getStage() == ProductionStage.PLATE_PREPARATION
                    && items.findByPlateTaskId(t.getId()).isEmpty())
        .map(t -> orders.findByOrderNumber(t.getOrderNumber()).orElse(null))
        .filter(Objects::nonNull)
        .filter(
            o ->
                workflow.active(o)
                    && workflow.paid(o)
                    && workflow.codes(o.getOrderNumber()).isEmpty())
        .map(workflow::view)
        .toList();
  }

  public List<Map<String, Object>> list() {
    requireData();
    return batches.findAllByOrderByIdDesc().stream()
        .filter(this::readable)
        .map(this::view)
        .toList();
  }

  public Map<String, Object> detail(Long id) {
    return view(read(id));
  }

  private Map<String, Object> view(PrintBatch b) {
    var members = items.findByBatchIdOrderByIdAsc(b.getId());
    var used = materials(members);
    boolean canEdit =
        b.getStatus().equals("DRAFT")
            && b.getCreatorId().equals(access.current().getId())
            && access.eligible(access.current().getId(), WorkRole.DESIGN_QC) != null;
    var actions = new ArrayList<String>();
    if (canEdit) actions.addAll(List.of("EDIT_BATCH", "UPLOAD_BATCH_FILE", "CONFIRM_BATCH"));
    if (b.getStatus().equals("DRAFT") && (canEdit || access.has(ASSIGN_WORK)))
      actions.add("CANCEL_BATCH");
    if (b.getStatus().equals("CONFIRMED") && access.has(ASSIGN_WORK))
      actions.add("ASSIGN_PRINT_BATCH");
    var problems = new ArrayList<String>();
    var memberViews =
        members.stream()
            .map(i -> orders.findByOrderNumber(i.getOrderNumber()).orElseThrow(this::missing))
            .map(workflow::view)
            .toList();
    if (memberViews.stream()
        .anyMatch(
            o ->
                !"ACTIVE".equals(o.get("orderStatus"))
                    || !((List<?>) o.get("blockingIssues")).isEmpty()))
      problems.add("포함 주문의 상태·담당자 또는 차단 문제를 확인해 주세요.");
    return map(
        "id",
        b.getId(),
        "version",
        b.getVersion(),
        "status",
        b.getStatus(),
        "layoutRevision",
        b.getLayoutRevision(),
        "printerName",
        b.getPrinterName(),
        "printingAssigneeId",
        b.getPrintingAssigneeId(),
        "printingAssigneeName",
        b.getPrintingAssigneeId() == null
            ? null
            : accounts.findById(b.getPrintingAssigneeId()).map(a -> a.getName()).orElse("이전 담당자"),
        "orders",
        memberViews,
        "allowedActions",
        actions,
        "blockingIssues",
        problems,
        "slots",
        slots.findByBatchIdOrderByIdAsc(b.getId()).stream()
            .map(
                s -> {
                  var m = used.get(s.getFilamentId());
                  return map(
                      "slotLabel",
                      s.getSlotLabel(),
                      "filamentId",
                      s.getFilamentId(),
                      "spoolId",
                      m == null ? null : m.getSpoolId(),
                      "colorName",
                      m == null ? null : m.getColorName(),
                      "material",
                      m == null ? null : m.getMaterial(),
                      "finish",
                      m == null ? null : m.getFinish());
                })
            .toList(),
        "artifacts",
        files.findByBatchIdOrderByIdDesc(b.getId()).stream()
            .filter(PrintBatchArtifact::isConfirmed)
            .map(
                a ->
                    map(
                        "id",
                        a.getId(),
                        "fileName",
                        a.getFileName(),
                        "size",
                        a.getExpectedSize(),
                        "currentLayout",
                        a.getId().equals(b.getArtifactId())
                            && a.getLayoutRevision() == b.getLayoutRevision()))
            .toList());
  }

  public Map<String, Object> save(Long id, String key, Map<String, Object> input) {
    creator();
    if (id != null) edit(read(id));
    return workflow.command(
        "plate-save:" + id,
        key,
        input,
        () -> {
          creator();
          var b = id == null ? PrintBatch.create(access.current().getId()) : fresh(id);
          if (id != null) {
            edit(b);
            version(b, input);
            draft(b);
          }
          var selected = selected(input);
          var previous =
              id == null ? List.<PrintBatchItem>of() : items.findByBatchIdOrderByIdAsc(id);
          var all = new TreeSet<>(selected.keySet());
          previous.forEach(i -> all.add(i.getOrderNumber()));
          var locked = lock(all);
          var newItems = new ArrayList<PrintBatchItem>();
          for (var entry : selected.entrySet()) {
            var o = locked.get(entry.getKey());
            workflow.version(o, entry.getValue());
            var t = eligible(o, id);
            newItems.add(PrintBatchItem.of(id, t, mappingTask(t)));
          }
          var used = materials(newItems);
          var newSlots = new ArrayList<PrintBatchSlot>();
          var labels = new HashSet<String>();
          var spoolIds = new HashSet<Long>();
          for (var slot : entries(input, "slots", 0, 64)) {
            String label =
                java.text.Normalizer.normalize(
                        text(slot, "slotLabel", 30), java.text.Normalizer.Form.NFKC)
                    .replaceAll("(?U)\\s+", " ")
                    .strip()
                    .toUpperCase(Locale.ROOT);
            Long filament = number(slot, "filamentId");
            if (label.isBlank()
                || label.length() > 30
                || !labels.add(label)
                || !spoolIds.add(filament)
                || !used.containsKey(filament)) throw bad("슬롯 이름과 실제 필라멘트가 중복되거나 포함 주문과 다릅니다.");
            usable(filament);
            newSlots.add(PrintBatchSlot.of(id, label, filament));
          }
          Long printer =
              input.get("printingAssigneeId") == null ? null : number(input, "printingAssigneeId");
          if (printer != null && !printingEligible(printer)) throw bad("활성 출력 담당자를 선택해 주세요.");
          String printerName = text(input, "printerName", 80);
          String fingerprint;
          try {
            fingerprint =
                java.util.HexFormat.of()
                    .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                            .digest(
                                snapshot(
                                        map(
                                            "printer",
                                            printerName,
                                            "tasks",
                                            newItems.stream()
                                                .map(PrintBatchItem::getPlateTaskId)
                                                .sorted()
                                                .toList(),
                                            "slots",
                                            newSlots.stream()
                                                .sorted(
                                                    Comparator.comparing(
                                                        PrintBatchSlot::getSlotLabel))
                                                .map(
                                                    s ->
                                                        map(
                                                            "label",
                                                            s.getSlotLabel(),
                                                            "filament",
                                                            s.getFilamentId()))
                                                .toList()))
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
          } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
          }
          b.configure(printerName, printer, fingerprint, clock.instant());
          batches.saveAndFlush(b);
          String before = snapshot(previous.stream().map(PrintBatchItem::getOrderNumber).toList());
          items.deleteAll(previous);
          items.flush();
          items.saveAllAndFlush(
              newItems.stream()
                  .map(
                      i ->
                          PrintBatchItem.of(
                              b.getId(),
                              tasks.findById(i.getPlateTaskId()).orElseThrow(),
                              i.getMappingTaskId()))
                  .toList());
          slots.deleteAll(slots.findByBatchIdOrderByIdAsc(b.getId()));
          slots.flush();
          slots.saveAllAndFlush(
              newSlots.stream()
                  .map(s -> PrintBatchSlot.of(b.getId(), s.getSlotLabel(), s.getFilamentId()))
                  .toList());
          for (var o : locked.values()) {
            workflow.touch(o);
            workflow.audit(
                o.getOrderNumber(), "SAVE_PRINT_PLATE", null, b.getId().toString(), null);
          }
          workflow.audit(
              "plate:" + b.getId(),
              "SAVE_PRINT_PLATE",
              before,
              snapshot(
                  map(
                      "orders",
                      selected.keySet(),
                      "printer",
                      printerName,
                      "slots",
                      input.get("slots"),
                      "printingAssigneeId",
                      printer)),
              null);
          return view(b);
        });
  }

  private Map<String, GoodsSurveyFulfillment> checkMembers(
      PrintBatch b, Map<String, Object> input) {
    var selected = selected(input);
    var members = items.findByBatchIdOrderByIdAsc(b.getId());
    if (!selected
        .keySet()
        .equals(new TreeSet<>(members.stream().map(PrintBatchItem::getOrderNumber).toList())))
      throw bad("플레이트의 모든 주문을 확인해 주세요.");
    var locked = lock(selected.keySet());
    for (var entry : selected.entrySet())
      workflow.version(locked.get(entry.getKey()), entry.getValue());
    for (var i : members) {
      var task = eligible(locked.get(i.getOrderNumber()), b.getId());
      if (!task.getId().equals(i.getPlateTaskId())) throw bad("플레이트의 작업 회차가 변경됐습니다.");
    }
    return locked;
  }

  public Map<String, Object> confirm(Long id, String key, Map<String, Object> input) {
    edit(read(id));
    return workflow.command(
        "plate-confirm:" + id,
        key,
        input,
        () -> {
          var b = fresh(id);
          edit(b);
          version(b, input);
          draft(b);
          var locked = checkMembers(b, input);
          validateLayout(b, true);
          if (!Boolean.TRUE.equals(input.get("layoutChecked")))
            throw bad("출력 파일의 주문 배치와 필라멘트 슬롯을 확인해 주세요.");
          if (files.findByBatchIdOrderByIdDesc(id).stream()
              .noneMatch(
                  f ->
                      f.getId().equals(b.getArtifactId())
                          && f.isConfirmed()
                          && f.getLayoutRevision() == b.getLayoutRevision()))
            throw new WorkflowException(
                422, "ARTIFACT_MISSING", "현재 구성의 3MF 또는 G-code 파일을 등록해 주세요.");
          for (var i : items.findByBatchIdOrderByIdAsc(id)) {
            var t = tasks.findById(i.getPlateTaskId()).orElseThrow();
            t.complete(clock.instant());
            tasks.saveAndFlush(
                ProductionTask.create(
                    i.getOrderNumber(),
                    ProductionStage.PRINT_QUEUE,
                    b.getPrintingAssigneeId(),
                    t.getAttempt()));
            var o = locked.get(i.getOrderNumber());
            o.moveProduction(ProductionStage.PRINT_QUEUE);
            workflow.touch(o);
            workflow.audit(
                i.getOrderNumber(),
                "CONFIRM_PRINT_PLATE",
                "PLATE_PREPARATION",
                "PRINT_QUEUE",
                "PB-" + id);
          }
          b.confirm(clock.instant());
          batches.saveAndFlush(b);
          workflow.audit("plate:" + id, "CONFIRM_PRINT_PLATE", "DRAFT", "CONFIRMED", null);
          return view(b);
        });
  }

  public Map<String, Object> cancel(Long id, String key, Map<String, Object> input) {
    read(id);
    return workflow.command(
        "plate-cancel:" + id,
        key,
        input,
        () -> {
          var b = fresh(id);
          requireData();
          if (!readable(b)) throw missing();
          if (!access.has(ASSIGN_WORK)) edit(b);
          version(b, input);
          if (!b.getStatus().equals("DRAFT")) throw bad("임시 플레이트만 취소할 수 있습니다.");
          var members = items.findByBatchIdOrderByIdAsc(id);
          var locked = lock(members.stream().map(PrintBatchItem::getOrderNumber).toList());
          items.deleteAll(members);
          items.flush();
          slots.deleteAll(slots.findByBatchIdOrderByIdAsc(id));
          slots.flush();
          for (var o : locked.values()) {
            workflow.touch(o);
            workflow.audit(o.getOrderNumber(), "CANCEL_PRINT_PLATE", "PB-" + id, null, null);
          }
          b.cancel(clock.instant());
          batches.saveAndFlush(b);
          workflow.audit(
              "plate:" + id, "CANCEL_PRINT_PLATE", snapshot(locked.keySet()), null, null);
          return view(b);
        });
  }

  public Map<String, Object> assign(Long id, String key, Map<String, Object> input) {
    access.require(ASSIGN_WORK);
    read(id);
    return workflow.command(
        "plate-assign:" + id,
        key,
        input,
        () -> {
          access.require(ASSIGN_WORK);
          var b = fresh(id);
          requireData();
          if (!readable(b)) throw missing();
          version(b, input);
          if (!b.getStatus().equals("CONFIRMED")) throw bad("출력 대기 플레이트의 담당자를 변경해 주세요.");
          Long target = number(input, "printingAssigneeId");
          if (!printingEligible(target)) throw bad("활성 출력 담당자를 선택해 주세요.");
          var expected = selected(input);
          var members = items.findByBatchIdOrderByIdAsc(id);
          if (!expected
              .keySet()
              .equals(new TreeSet<>(members.stream().map(PrintBatchItem::getOrderNumber).toList())))
            throw bad("모든 포함 주문을 확인해 주세요.");
          var locked = lock(expected.keySet());
          for (var o : locked.values()) {
            workflow.version(o, expected.get(o.getOrderNumber()));
            var t = workflow.currentTask(o.getOrderNumber());
            if (t == null
                || t.getStage() != ProductionStage.PRINT_QUEUE
                || !t.getStatus().equals("WAITING")
                || !workflow.active(o)) throw bad("모든 주문이 출력 대기 중일 때 변경할 수 있습니다.");
            t.assign(target);
            workflow.touch(o);
            workflow.audit(
                o.getOrderNumber(),
                "ASSIGN_PRINT_BATCH",
                String.valueOf(b.getPrintingAssigneeId()),
                target.toString(),
                null);
          }
          b.configure(b.getPrinterName(), target, b.getLayoutFingerprint(), clock.instant());
          batches.saveAndFlush(b);
          return view(b);
        });
  }

  public Map<String, Object> upload(Long id, String key, Map<String, Object> input) {
    edit(read(id));
    var result =
        workflow.command(
            "plate-upload:" + id,
            key,
            input,
            () -> {
              var b = fresh(id);
              edit(b);
              version(b, input);
              draft(b);
              validateLayout(b, false);
              String name = text(input, "fileName", 200);
              long size = number(input, "size");
              if (name.isBlank()
                  || name.contains("/")
                  || name.contains("\\")
                  || !name.toLowerCase(Locale.ROOT).matches(".+\\.(3mf|gcode)")
                  || size < 1
                  || size > 100L * 1024 * 1024) throw bad("3MF 또는 G-code 파일을 100MB 이하로 선택해 주세요.");
              var f =
                  files.saveAndFlush(
                      PrintBatchArtifact.pending(
                          b,
                          access.current().getId(),
                          name,
                          size,
                          clock.instant().plusSeconds(600)));
              b.touch(clock.instant());
              batches.saveAndFlush(b);
              workflow.audit("plate:" + id, "REQUEST_PLATE_FILE", null, f.getId(), null);
              return map("artifactId", f.getId(), "version", b.getVersion());
            });
    var f = files.findById(result.get("artifactId").toString()).orElseThrow(this::missing);
    var current = fresh(id);
    edit(current);
    draft(current);
    if (f.isConfirmed() || f.getLayoutRevision() != current.getLayoutRevision())
      throw bad("이미 등록됐거나 구성이 변경된 파일입니다. 새 파일을 선택해 주세요.");
    if (!f.getExpiresAt().isAfter(clock.instant())) throw bad("업로드 시간이 만료됐습니다. 파일을 다시 선택해 주세요.");
    var link =
        storage.presignUpload(
            f.getObjectKey(),
            "application/octet-stream",
            f.getExpectedSize(),
            Duration.between(clock.instant(), f.getExpiresAt()),
            f.getExpiresAt());
    result.put("url", link.url());
    result.put("headers", link.headers());
    return result;
  }

  public Map<String, Object> confirmFile(
      Long id, String fileId, String key, Map<String, Object> input) {
    edit(read(id));
    return workflow.command(
        "plate-file:" + id + ":" + fileId,
        key,
        input,
        () -> {
          var b = fresh(id);
          edit(b);
          version(b, input);
          draft(b);
          var f = files.findById(fileId).orElseThrow(this::missing);
          entityManager.refresh(f);
          if (!f.getBatchId().equals(id) || !f.getUploaderId().equals(access.current().getId()))
            throw missing();
          if (f.getLayoutRevision() != b.getLayoutRevision()
              || !f.getExpiresAt().isAfter(clock.instant()))
            throw bad("구성이 변경됐거나 업로드 시간이 만료됐습니다. 다시 등록해 주세요.");
          var stored = storage.head(f.getObjectKey());
          byte[] signature = stored.signatureBytes();
          boolean valid = signature != null && signature.length > 0;
          if (f.getFileName().toLowerCase(Locale.ROOT).endsWith(".3mf"))
            valid =
                valid
                    && signature.length >= 4
                    && signature[0] == 80
                    && signature[1] == 75
                    && signature[2] == 3
                    && signature[3] == 4;
          else if (valid)
            for (byte v : signature)
              if (v == 0) {
                valid = false;
                break;
              }
          if (stored.contentLength() != f.getExpectedSize()
              || !"application/octet-stream".equals(stored.contentType())
              || !valid) throw bad("실제 업로드 파일의 크기·형식을 확인해 주세요.");
          f.confirm();
          files.saveAndFlush(f);
          b.useArtifact(f.getId(), clock.instant());
          batches.saveAndFlush(b);
          workflow.audit("plate:" + id, "CONFIRM_PLATE_FILE", null, fileId, null);
          return view(b);
        });
  }

  public Map<String, Object> download(Long id, String fileId) {
    var b = read(id);
    access.require(DOWNLOAD_PRODUCTION_FILES);
    var f = files.findById(fileId).orElseThrow(this::missing);
    if (!f.getBatchId().equals(id)
        || !f.isConfirmed()
        || !f.getId().equals(b.getArtifactId())
        || b.getStatus().equals("CANCELED")
        || f.getLayoutRevision() != b.getLayoutRevision()) throw missing();
    for (var i : items.findByBatchIdOrderByIdAsc(id)) {
      var o = orders.findByOrderNumber(i.getOrderNumber()).orElseThrow(this::missing);
      if (Set.of("CANCELED", "EXPIRED").contains(o.orderStatus())
          || o.getDeleteAfter() != null && !o.getDeleteAfter().isAfter(clock.instant()))
        throw bad("포함 주문의 파일 열람 기간이 끝났습니다.");
    }
    var link =
        storage.presignDownload(
            f.getObjectKey(), Duration.ofMinutes(5), clock.instant().plusSeconds(300));
    workflow.audit("plate:" + id, "DOWNLOAD_PLATE_FILE", null, fileId, null);
    return map("url", link.url(), "expiresAt", link.expiresAt());
  }
}
