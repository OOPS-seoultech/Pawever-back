package com.pawever.backend.workflow;

import static com.pawever.backend.admin.entity.PermissionKey.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawever.backend.admin.entity.*;
import com.pawever.backend.admin.repository.AdminAccountRepository;
import com.pawever.backend.goodssurvey.entity.*;
import com.pawever.backend.goodssurvey.repository.*;
import com.pawever.backend.goodssurvey.service.GoodsSurveyPhotoStorage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.HexFormat;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class WorkflowService {
  private final GoodsSurveyFulfillmentRepository orders;
  private final ProductionTaskRepository tasks;
  private final ProductionArtifactRepository artifacts;
  private final WorkflowIssueRepository issues;
  private final WorkflowCommandRepository commands;
  private final WorkflowAuditRepository audits;
  private final WorkflowSettingsRepository settings;
  private final AdminAccountRepository accounts;
  private final StaffPermissionOverrideRepository overrides;
  private final GoodsSurveyPhotoRepository photos;
  private final GoodsSurveyPhotoStorage storage;
  private final StaffPermissions access;
  private final Clock clock;
  private final jakarta.persistence.EntityManager entityManager;
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  private static final Set<String> KINDS = Set.of("MULTIVIEW", "MODEL_SOURCE", "PRINT_MODEL");

  private Map<String, Object> map(Object... args) {
    var m = new LinkedHashMap<String, Object>();
    for (int i = 0; i < args.length; i += 2) m.put((String) args[i], args[i + 1]);
    return m;
  }

  private WorkflowException bad(String message) {
    return new WorkflowException(400, "INVALID_INPUT", message);
  }

  private WorkflowException conflict(Object latest) {
    return new WorkflowException(
        409, "VERSION_CONFLICT", "다른 작업자가 변경했습니다. 최신 내용을 확인해 주세요.", latest);
  }

  private long number(Map<String, Object> b, String name) {
    var v = b.get(name);
    if (!(v instanceof Number n) || n.doubleValue() != n.longValue() || n.longValue() < 0)
      throw bad(name + " 값을 확인해 주세요.");
    return n.longValue();
  }

  private Long id(Map<String, Object> b, String name) {
    return b.get(name) == null ? null : number(b, name);
  }

  private String text(Map<String, Object> b, String name, int limit) {
    Object v = b.get(name);
    if (v == null) return "";
    if (!(v instanceof String s) || s.length() > limit) throw bad(name + " 값을 확인해 주세요.");
    return s.strip();
  }

  private GoodsSurveyFulfillment order(String number) {
    return orders
        .findByOrderNumber(number)
        .orElseThrow(() -> new WorkflowException(404, "NOT_FOUND", "주문을 찾을 수 없습니다."));
  }

  private GoodsSurveyFulfillment locked(String number) {
    var o =
        orders
            .lockByOrderNumber(number)
            .orElseThrow(() -> new WorkflowException(404, "NOT_FOUND", "주문을 찾을 수 없습니다."));
    entityManager.refresh(o);
    return o;
  }

  private boolean active(GoodsSurveyFulfillment o) {
    return o.orderStatus().equals("ACTIVE")
        && o.shipmentStatus().equals("NOT_READY")
        && !o.paymentStatus().equals("REFUND_PENDING");
  }

  private boolean paid(GoodsSurveyFulfillment o) {
    return Set.of("CONFIRMED", "NOT_REQUIRED").contains(o.paymentStatus());
  }

  private ProductionTask currentTask(String number) {
    return tasks.findByOrderNumberOrderByIdAsc(number).stream()
        .filter(t -> !t.getStatus().equals("COMPLETED"))
        .reduce((a, b) -> b)
        .orElse(null);
  }

  private ProductionTask task(Long id) {
    return tasks
        .findById(id)
        .orElseThrow(() -> new WorkflowException(404, "NOT_FOUND", "작업을 찾을 수 없습니다."));
  }

  private void version(GoodsSurveyFulfillment o, Map<String, Object> b) {
    if (o.getVersion() != number(b, "version")) throw conflict(view(o));
  }

  private void touch(GoodsSurveyFulfillment o) {
    o.touchWorkflow(clock.instant());
    orders.saveAndFlush(o);
  }

  private List<String> codes(String number) {
    return issues.findByOrderNumber(number).stream().map(WorkflowIssue::getCode).toList();
  }

  private void issue(String number, String code, boolean present) {
    var existing =
        issues.findByOrderNumber(number).stream().filter(i -> i.getCode().equals(code)).toList();
    if (present && existing.isEmpty()) issues.saveAndFlush(WorkflowIssue.of(number, code));
    if (!present && !existing.isEmpty()) {
      issues.deleteAll(existing);
      issues.flush();
    }
  }

  private boolean enoughPhotos(GoodsSurveyFulfillment o) {
    return photos.findByResponseId(o.getResponseId()).stream()
            .filter(p -> p.getStatus() == GoodsSurveyPhotoStatus.CONFIRMED)
            .count()
        >= 3;
  }

  private void audit(String resource, String action, String before, String after, String reason) {
    audits.save(
        WorkflowAudit.of(
            resource, access.current().getId(), action, before, after, reason, clock.instant()));
  }

  private WorkflowSettings config() {
    return settings.findById(1L).orElseGet(() -> settings.saveAndFlush(new WorkflowSettings()));
  }

  private Map<String, Object> command(
      String resource, String key, Map<String, Object> body, Supplier<Map<String, Object>> action) {
    // Five-person operations: account-row lock orders all staff mutations, including duplicate
    // keys.
    // Existing order writers still use the order optimistic version to detect races.
    accounts.lockAccounts().forEach(entityManager::refresh);
    var actor = access.current();
    if (key == null || key.isBlank() || key.length() > 100) throw bad("요청 식별자가 필요합니다.");
    try {
      String fingerprint =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          (resource + "\n" + json.writeValueAsString(new TreeMap<>(body)))
                              .getBytes(StandardCharsets.UTF_8)));
      var old = commands.findByActorIdAndCommandKey(actor.getId(), key);
      if (old.isPresent()) {
        if (!old.get().getFingerprint().equals(fingerprint))
          throw new WorkflowException(409, "IDEMPOTENCY_CONFLICT", "같은 요청 식별자로 다른 작업을 보낼 수 없습니다.");
        return json.readValue(old.get().getResultJson(), LinkedHashMap.class);
      }
      var result = action.get();
      String number =
          result.get("orderNumber") instanceof String n
              ? n
              : result.get("artifactId") instanceof String artifactId
                  ? artifacts
                      .findById(artifactId)
                      .map(ProductionArtifact::getOrderNumber)
                      .orElse(null)
                  : null;
      commands.saveAndFlush(
          WorkflowCommand.of(
              actor.getId(), key, fingerprint, json.writeValueAsString(result), number));
      return result;
    } catch (WorkflowException e) {
      throw e;
    } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public Map<String, Object> me() {
    var a = access.current();
    return map(
        "id",
        a.getId(),
        "name",
        a.getName(),
        "role",
        a.getRole(),
        "workRoles",
        a.getWorkRoles(),
        "permissions",
        access.effective(a));
  }

  public Map<String, Object> detail(String number) {
    access.read(number);
    return view(order(number));
  }

  private Map<String, Object> view(GoodsSurveyFulfillment o) {
    var actor = access.current();
    var permissions = access.effective(actor);
    var t = currentTask(o.getOrderNumber());
    var block = new ArrayList<>(codes(o.getOrderNumber()));
    if (t != null
        && access.eligible(
                t.getAssigneeId(),
                t.getStage() == ProductionStage.MODEL_REVIEW
                    ? WorkRole.DESIGN_QC
                    : WorkRole.MODELING)
            == null
        && !block.contains("UNASSIGNED")) block.add("UNASSIGNED");
    var actions = new ArrayList<String>();
    if (active(o)) {
      if (o.getStatus() == GoodsOrderStatus.PAYMENT_PENDING
          && permissions.contains(CONFIRM_PAYMENT)) actions.add("CONFIRM_PAYMENT");
      if (permissions.contains(ASSIGN_WORK) && paid(o) && o.getProductionStage() != null)
        actions.add("ASSIGN_TASK");
      if (permissions.contains(ASSIGN_WORK)
          && paid(o)
          && o.getStatus() != GoodsOrderStatus.IN_PRODUCTION
          && t == null) actions.add("ENROLL");
      if (t != null
          && Objects.equals(t.getAssigneeId(), actor.getId())
          && t.getStage() == ProductionStage.MODELING
          && permissions.contains(COMPLETE_MODELING)
          && paid(o)
          && block.isEmpty()) {
        if (t.getStatus().equals("WAITING")) actions.add("START_TASK");
        if (t.getStatus().equals("IN_PROGRESS")) {
          actions.add("UPLOAD_ARTIFACT");
          actions.add("COMPLETE_MODELING");
        }
      }
    }
    var assignee =
        t == null || t.getAssigneeId() == null
            ? null
            : accounts
                .findById(t.getAssigneeId())
                .map(a -> map("id", a.getId(), "name", a.getName()))
                .orElse(null);
    var fs =
        permissions.contains(VIEW_PRODUCTION_FILES)
            ? artifacts.findByOrderNumberOrderByIdAsc(o.getOrderNumber()).stream()
                .filter(ProductionArtifact::isConfirmed)
                .map(
                    a ->
                        map(
                            "id",
                            a.getId(),
                            "kind",
                            a.getKind(),
                            "fileName",
                            a.getFileName(),
                            "size",
                            a.getExpectedSize()))
                .toList()
            : List.of();
    return map(
        "orderNumber",
        o.getOrderNumber(),
        "petName",
        o.getPetName(),
        "guardianName",
        permissions.contains(VIEW_CUSTOMER_IDENTITY) ? o.getGuardianName() : null,
        "goodsType",
        o.getGoodsType(),
        "customGoods",
        o.getCustomGoods(),
        "keyringAdded",
        o.isKeyringAdded(),
        "orderStatus",
        o.orderStatus(),
        "paymentStatus",
        o.paymentStatus(),
        "productionStage",
        o.getProductionStage() == null ? "BLOCKED" : o.getProductionStage().name(),
        "requiresMigrationReview",
        o.getStatus() == GoodsOrderStatus.IN_PRODUCTION && o.getProductionStage() == null,
        "shipmentStatus",
        o.shipmentStatus(),
        "version",
        o.getVersion(),
        "taskId",
        t == null ? null : t.getId(),
        "taskStatus",
        t == null ? null : t.getStatus(),
        "assignee",
        assignee,
        "blockingIssues",
        block,
        "allowedActions",
        actions,
        "artifacts",
        fs,
        "expectedAmount",
        permissions.contains(VIEW_PAYMENT) ? o.getPaymentAmountKrw() : null);
  }

  public List<Map<String, Object>> queue() {
    access.require(VIEW_ALL_ORDERS);
    access.require(VIEW_ORDER_BASIC);
    return orders.findByStatusInOrderByCreatedAtDesc(EnumSet.allOf(GoodsOrderStatus.class)).stream()
        .map(this::view)
        .toList();
  }

  public List<Map<String, Object>> mine() {
    var a = access.require(VIEW_ORDER_BASIC);
    return tasks.findByAssigneeIdAndStatusNotOrderByIdAsc(a.getId(), "COMPLETED").stream()
        .map(t -> orders.findByOrderNumber(t.getOrderNumber()).orElse(null))
        .filter(Objects::nonNull)
        .filter(this::active)
        .map(this::view)
        .toList();
  }

  private ProductionTask createModeling(GoodsSurveyFulfillment o) {
    var t = currentTask(o.getOrderNumber());
    if (t != null) return t;
    Long assignee = access.eligible(config().getModeling(), WorkRole.MODELING);
    t =
        tasks.saveAndFlush(
            ProductionTask.create(o.getOrderNumber(), ProductionStage.MODELING, assignee));
    o.moveProduction(ProductionStage.MODELING_QUEUE);
    issue(o.getOrderNumber(), "UNASSIGNED", assignee == null);
    issue(o.getOrderNumber(), "PHOTO_INSUFFICIENT", !enoughPhotos(o));
    return t;
  }

  public Map<String, Object> confirm(String number, String key, Map<String, Object> b) {
    access.require(CONFIRM_PAYMENT);
    access.require(VIEW_PAYMENT);
    access.read(number);
    return command(
        "payment:" + number,
        key,
        b,
        () -> {
          access.require(CONFIRM_PAYMENT);
          access.require(VIEW_PAYMENT);
          access.read(number);
          var o = locked(number);
          version(o, b);
          if (o.getStatus() != GoodsOrderStatus.PAYMENT_PENDING) throw bad("입금 확인 대기 주문이 아닙니다.");
          if (number(b, "amount") != o.getPaymentAmountKrw()) {
            issue(number, "PAYMENT_MISMATCH", true);
            touch(o);
            audit(
                number,
                "PAYMENT_MISMATCH",
                o.paymentStatus(),
                o.paymentStatus(),
                text(b, "memo", 300));
            return view(o);
          }
          o.confirmManually(access.current().getId(), o.getPaymentAmountKrw(), clock.instant());
          issue(number, "PAYMENT_MISMATCH", false);
          createModeling(o);
          touch(o);
          audit(number, "CONFIRM_PAYMENT", "PENDING", "CONFIRMED", text(b, "memo", 300));
          return view(o);
        });
  }

  public Map<String, Object> enroll(String number, String key, Map<String, Object> b) {
    access.require(ASSIGN_WORK);
    access.read(number);
    return command(
        "enroll:" + number,
        key,
        b,
        () -> {
          access.require(ASSIGN_WORK);
          access.read(number);
          var o = locked(number);
          version(o, b);
          if (!Set.of(GoodsOrderStatus.PAYMENT_COMPLETED, GoodsOrderStatus.LEGACY_FREE)
              .contains(o.getStatus())) throw bad("결제가 확인된 제작 대기 주문만 배정할 수 있습니다.");
          createModeling(o);
          touch(o);
          audit(number, "ENROLL", null, "MODELING_QUEUE", null);
          return view(o);
        });
  }

  private void own(ProductionTask t) {
    access.read(t.getOrderNumber());
    if (!Objects.equals(t.getAssigneeId(), access.current().getId()))
      throw new WorkflowException(403, "FORBIDDEN", "배정된 작업만 처리할 수 있습니다.");
    access.require(COMPLETE_MODELING);
    if (access.eligible(t.getAssigneeId(), WorkRole.MODELING) == null)
      throw new WorkflowException(403, "FORBIDDEN", "모델링 역할의 활성 담당자만 처리할 수 있습니다.");
  }

  public Map<String, Object> start(Long taskId, String key, Map<String, Object> b) {
    var t = task(taskId);
    own(t);
    return command(
        "start:" + taskId,
        key,
        b,
        () -> {
          entityManager.refresh(t);
          own(t);
          var o = locked(t.getOrderNumber());
          version(o, b);
          if (!active(o)
              || !paid(o)
              || t.getStage() != ProductionStage.MODELING
              || !t.getStatus().equals("WAITING")) throw bad("시작할 수 없는 작업입니다.");
          issue(o.getOrderNumber(), "PHOTO_INSUFFICIENT", !enoughPhotos(o));
          if (!codes(o.getOrderNumber()).isEmpty()) throw bad("차단 문제를 먼저 해결해 주세요.");
          t.start(clock.instant());
          o.moveProduction(ProductionStage.MODELING);
          touch(o);
          audit(o.getOrderNumber(), "START_TASK", "MODELING_QUEUE", "MODELING", null);
          return view(o);
        });
  }

  public Map<String, Object> complete(Long taskId, String key, Map<String, Object> b) {
    var t = task(taskId);
    own(t);
    return command(
        "complete:" + taskId,
        key,
        b,
        () -> {
          entityManager.refresh(t);
          own(t);
          var o = locked(t.getOrderNumber());
          version(o, b);
          if (!active(o)
              || !paid(o)
              || !t.getStatus().equals("IN_PROGRESS")
              || t.getStage() != ProductionStage.MODELING) throw bad("완료할 수 없는 작업입니다.");
          var present = new HashSet<String>();
          artifacts.findByOrderNumberOrderByIdAsc(o.getOrderNumber()).stream()
              .filter(a -> a.isConfirmed() && a.getTaskId().equals(taskId))
              .forEach(a -> present.add(a.getKind()));
          var missing = new TreeSet<>(KINDS);
          missing.removeAll(present);
          if (!missing.isEmpty())
            throw new WorkflowException(
                422, "ARTIFACT_MISSING", "필수 파일을 등록해 주세요: " + String.join(", ", missing));
          if (!codes(o.getOrderNumber()).isEmpty()) throw bad("차단 문제를 먼저 해결해 주세요.");
          t.complete(clock.instant());
          Long reviewer = access.eligible(config().getReview(), WorkRole.DESIGN_QC);
          tasks.saveAndFlush(
              ProductionTask.create(o.getOrderNumber(), ProductionStage.MODEL_REVIEW, reviewer));
          o.moveProduction(ProductionStage.MODEL_REVIEW);
          issue(o.getOrderNumber(), "UNASSIGNED", reviewer == null);
          touch(o);
          audit(o.getOrderNumber(), "COMPLETE_MODELING", "MODELING", "MODEL_REVIEW", null);
          return view(o);
        });
  }

  public Map<String, Object> assign(String number, String key, Map<String, Object> b) {
    access.require(ASSIGN_WORK);
    access.read(number);
    return command(
        "assign:" + number,
        key,
        b,
        () -> {
          access.require(ASSIGN_WORK);
          access.read(number);
          var o = locked(number);
          version(o, b);
          var t = currentTask(number);
          if (t == null || !active(o) || !paid(o)) throw bad("배정할 활성 작업이 없습니다.");
          Long target = id(b, "assigneeId");
          WorkRole role =
              t.getStage() == ProductionStage.MODEL_REVIEW ? WorkRole.DESIGN_QC : WorkRole.MODELING;
          if (access.eligible(target, role) == null) throw bad("해당 역할의 활성 담당자를 선택해 주세요.");
          String before = String.valueOf(t.getAssigneeId());
          t.assign(target);
          issue(number, "UNASSIGNED", false);
          issue(
              number,
              "PHOTO_INSUFFICIENT",
              t.getStage() == ProductionStage.MODELING && !enoughPhotos(o));
          touch(o);
          audit(number, "ASSIGN_TASK", before, String.valueOf(target), text(b, "reason", 300));
          return view(o);
        });
  }

  public Map<String, Object> defaults() {
    access.require(MANAGE_OPERATION_SETTINGS);
    var c = config();
    return map("modeling", c.getModeling(), "review", c.getReview(), "version", c.getVersion());
  }

  public Map<String, Object> setDefaults(String key, Map<String, Object> b) {
    access.require(MANAGE_OPERATION_SETTINGS);
    return command(
        "default-assignees",
        key,
        b,
        () -> {
          access.require(MANAGE_OPERATION_SETTINGS);
          var c = config();
          if (c.getVersion() != number(b, "version")) throw conflict(defaults());
          Long m = id(b, "modeling"), r = id(b, "review");
          if (m != null && access.eligible(m, WorkRole.MODELING) == null
              || r != null && access.eligible(r, WorkRole.DESIGN_QC) == null)
            throw bad("역할에 맞는 활성 담당자를 선택해 주세요.");
          String before = c.getModeling() + "/" + c.getReview();
          c.change(m, r);
          settings.saveAndFlush(c);
          audit("settings", "DEFAULT_ASSIGNEES", before, m + "/" + r, null);
          return defaults();
        });
  }

  public List<Map<String, Object>> staff() {
    if (!access.has(MANAGE_ACCOUNTS)) access.require(ASSIGN_WORK);
    return accounts.findAllByOrderByCreatedAtAsc().stream()
        .map(
            a ->
                map(
                    "id",
                    a.getId(),
                    "name",
                    a.getName(),
                    "role",
                    a.getRole(),
                    "status",
                    a.getStatus(),
                    "workRoles",
                    a.getWorkRoles(),
                    "version",
                    a.getVersion()))
        .toList();
  }

  public Map<String, Object> roles(Long id, String key, Map<String, Object> b) {
    access.require(MANAGE_ACCOUNTS);
    var actor = access.current();
    if (actor.getId().equals(id)) throw bad("자신의 권한은 변경할 수 없습니다.");
    return command(
        "roles:" + id,
        key,
        b,
        () -> {
          access.require(MANAGE_ACCOUNTS);
          var a = accounts.findById(id).orElseThrow(() -> bad("계정을 찾을 수 없습니다."));
          if (a.getVersion() != number(b, "version")) throw conflict(staff());
          if (text(b, "reason", 300).isBlank()) throw bad("권한 변경 사유가 필요합니다.");
          AdminRole role;
          Set<WorkRole> work = new HashSet<>();
          try {
            role = AdminRole.valueOf(text(b, "role", 30));
            for (Object v : (List<?>) b.getOrDefault("workRoles", List.of()))
              work.add(WorkRole.valueOf(v.toString()));
          } catch (Exception e) {
            throw bad("역할을 확인해 주세요.");
          }
          if ((a.getRole() == AdminRole.OWNER || role == AdminRole.OWNER)
              && actor.getRole() != AdminRole.OWNER)
            throw new WorkflowException(403, "FORBIDDEN", "OWNER만 소유자 권한을 변경할 수 있습니다.");
          if (a.getRole() == AdminRole.OWNER
              && role != AdminRole.OWNER
              && accounts.findAll().stream()
                      .filter(x -> x.getRole() == AdminRole.OWNER && x.canSignIn())
                      .count()
                  <= 1) throw bad("마지막 OWNER를 해제할 수 없습니다.");
          String before = a.getRole() + ":" + a.getWorkRoles();
          a.changeRole(role);
          a.setWorkRoles(work);
          a.touchPermissions(clock.instant());
          accounts.saveAndFlush(a);
          audit("staff:" + id, "CHANGE_ROLES", before, role + ":" + work, text(b, "reason", 300));
          return map("id", id, "role", role, "workRoles", work, "version", a.getVersion());
        });
  }

  public Map<String, Object> upload(Long taskId, String key, Map<String, Object> b) {
    var t = task(taskId);
    own(t);
    access.require(VIEW_PRODUCTION_FILES);
    var result =
        command(
            "upload:" + taskId,
            key,
            b,
            () -> {
              entityManager.refresh(t);
              own(t);
              access.require(VIEW_PRODUCTION_FILES);
              var o = locked(t.getOrderNumber());
              version(o, b);
              if (!active(o) || !paid(o) || !t.getStatus().equals("IN_PROGRESS"))
                throw bad("진행 중인 작업에 파일을 등록해 주세요.");
              String kind = text(b, "kind", 30),
                  name = text(b, "fileName", 200),
                  type = text(b, "contentType", 100);
              long size = number(b, "size");
              if (!KINDS.contains(kind)
                  || size < 1
                  || size > 100L * 1024 * 1024
                  || !name.matches("[^/\\\\\\p{Cntrl}]+")
                  || name.equals(".")
                  || name.equals("..")) throw bad("파일 종류·이름·크기를 확인해 주세요.");
              String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
              Set<String> extensions =
                  kind.equals("MULTIVIEW")
                      ? Set.of("png", "jpg", "jpeg", "webp")
                      : kind.equals("PRINT_MODEL")
                          ? Set.of("stl", "3mf")
                          : Set.of("blend", "obj", "stl", "3mf", "fbx", "glb", "gltf");
              if (!extensions.contains(ext)) throw bad("허용되지 않은 파일 형식입니다.");
              if (kind.equals("MULTIVIEW")
                  && !Set.of("image/png", "image/jpeg", "image/webp").contains(type))
                throw bad("사진 형식을 확인해 주세요.");
              if (!kind.equals("MULTIVIEW")) type = "application/octet-stream";
              var a =
                  artifacts.saveAndFlush(
                      ProductionArtifact.pending(
                          UUID.randomUUID().toString(),
                          o.getOrderNumber(),
                          taskId,
                          access.current().getId(),
                          kind,
                          name,
                          type,
                          size,
                          clock.instant().plusSeconds(600)));
              touch(o);
              audit(o.getOrderNumber(), "REQUEST_ARTIFACT", null, a.getId(), null);
              return map("artifactId", a.getId(), "version", o.getVersion());
            });
    var a =
        artifacts
            .findById(result.get("artifactId").toString())
            .orElseThrow(() -> bad("업로드 요청이 만료됐습니다. 파일을 다시 선택해 주세요."));
    if (!a.getExpiresAt().isAfter(clock.instant())) throw bad("업로드 시간이 만료됐습니다. 파일을 다시 선택해 주세요.");
    var link =
        storage.presignUpload(
            a.getObjectKey(),
            a.getContentType(),
            a.getExpectedSize(),
            Duration.between(clock.instant(), a.getExpiresAt()),
            a.getExpiresAt());
    result.put("url", link.url());
    result.put("headers", link.headers());
    return result;
  }

  public Map<String, Object> confirmArtifact(String artifactId, String key, Map<String, Object> b) {
    var a = artifacts.findById(artifactId).orElseThrow(() -> bad("파일을 찾을 수 없습니다."));
    var t = task(a.getTaskId());
    own(t);
    return command(
        "confirm-artifact:" + artifactId,
        key,
        b,
        () -> {
          entityManager.refresh(t);
          entityManager.refresh(a);
          own(t);
          access.require(VIEW_PRODUCTION_FILES);
          var o = locked(a.getOrderNumber());
          version(o, b);
          if (!active(o)
              || !t.getStatus().equals("IN_PROGRESS")
              || !a.getUploaderId().equals(access.current().getId())) throw bad("파일을 확정할 수 없습니다.");
          var stored = storage.head(a.getObjectKey());
          if (stored.contentLength() != a.getExpectedSize()
              || !Objects.equals(stored.contentType(), a.getContentType()))
            throw bad("업로드 파일 정보가 일치하지 않습니다.");
          if (a.getKind().equals("MULTIVIEW")
              && !ArtifactFormats.matchesImage(
                  a.getFileName(), a.getContentType(), stored.signatureBytes()))
            throw bad("이미지 파일의 실제 형식이 일치하지 않습니다.");
          if (!a.isConfirmed() && a.getExpiresAt().isBefore(clock.instant()))
            throw bad("업로드 시간이 만료됐습니다. 다시 등록해 주세요.");
          a.confirm();
          artifacts.saveAndFlush(a);
          touch(o);
          audit(a.getOrderNumber(), "CONFIRM_ARTIFACT", null, a.getId(), null);
          return view(o);
        });
  }

  public Map<String, Object> download(String artifactId) {
    var a =
        artifacts
            .findById(artifactId)
            .orElseThrow(() -> new WorkflowException(404, "NOT_FOUND", "파일을 찾을 수 없습니다."));
    access.read(a.getOrderNumber());
    access.require(DOWNLOAD_PRODUCTION_FILES);
    order(a.getOrderNumber());
    var o = order(a.getOrderNumber());
    if (!a.isConfirmed()
        || Set.of("CANCELED", "EXPIRED").contains(o.orderStatus())
        || o.getDeleteAfter() != null && !o.getDeleteAfter().isAfter(clock.instant()))
      throw bad("열람 기간이 끝났거나 확정되지 않은 파일입니다.");
    var link =
        storage.presignDownload(
            a.getObjectKey(), Duration.ofMinutes(5), clock.instant().plusSeconds(300));
    audit(a.getOrderNumber(), "DOWNLOAD_ARTIFACT", null, a.getId(), null);
    return map("url", link.url(), "expiresAt", link.expiresAt());
  }

  public List<Map<String, Object>> timeline(String number) {
    access.read(number);
    return audits.findByResourceOrderByIdAsc(number).stream()
        .map(
            e ->
                map(
                    "action",
                    e.getAction(),
                    "createdAt",
                    e.getCreatedAt(),
                    "actorId",
                    e.getActorId()))
        .toList();
  }
}
