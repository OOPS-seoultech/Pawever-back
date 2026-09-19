package com.pawever.backend.workflow;

import static com.pawever.backend.admin.entity.PermissionKey.VIEW_ALL_ORDERS;

import com.pawever.backend.admin.entity.AdminRole;
import com.pawever.backend.admin.repository.AdminAccountRepository;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhotoStatus;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyPhotoRepository;
import com.pawever.backend.goodssurvey.service.GoodsSurveyPhotoStorage;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Completed-order AS access is intentionally separate from the normal workflow file route.
 * A production worker receives neither an order view nor a reusable storage key here.
 */
@Service
@RequiredArgsConstructor
public class AsCaseService {
  private static final String PRODUCTION_ARTIFACT = "PRODUCTION_ARTIFACT";
  private static final String CUSTOMER_PHOTO = "CUSTOMER_PHOTO";

  private final StaffPermissions access;
  private final AdminAccountRepository accounts;
  private final GoodsSurveyFulfillmentRepository orders;
  private final GoodsSurveyPhotoRepository photos;
  private final ProductionArtifactRepository artifacts;
  private final AsCaseRepository cases;
  private final AsCaseAccessGrantRepository grants;
  private final AsCaseAccessGrantAssetRepository grantAssets;
  private final WorkflowAuditRepository audits;
  private final GoodsSurveyPhotoStorage storage;
  private final Clock clock;

  public List<Map<String, Object>> completedOrders() {
    access.require(VIEW_ALL_ORDERS);
    return orders
        .findByStatusInOrderByCreatedAtDesc(EnumSet.of(GoodsOrderStatus.SHIPPED, GoodsOrderStatus.PICKED_UP))
        .stream()
        .map(
            order ->
                map(
                    "orderNumber", order.getOrderNumber(),
                    "goodsType", order.getGoodsType(),
                    "deliveryMethod", order.getDeliveryMethod().name(),
                    "orderStatus", order.orderStatus(),
                    "shipmentStatus", order.shipmentStatus(),
                    "trackingCompany", order.getTrackingCompany(),
                    "trackingNumber", order.getTrackingNumber(),
                    "deliveryCompletedAt", order.getDeliveryCompletedAt(),
                    "paymentAmountKrw", order.getPaymentAmountKrw(),
                    "paidAt", order.getPaidAt()))
        .toList();
  }

  public List<Map<String, Object>> listCases() {
    owner();
    return cases.findAllByOrderByIdDesc().stream().map(this::caseView).toList();
  }

  /** OWNER가 사건에 넣을 수 있는 식별자만 본다. 저장소 object key나 URL은 돌려주지 않는다. */
  public List<Map<String, Object>> availableAssets(Long caseId) {
    owner();
    var asCase = openCase(caseId);
    if (!retained(asCase)) return List.of();
    var result = new ArrayList<Map<String, Object>>();
    artifacts.findByOrderNumberOrderByIdAsc(asCase.getOrderNumber()).stream()
        .filter(ProductionArtifact::isConfirmed)
        .forEach(
            value ->
                result.add(
                    map(
                        "id", value.getId(),
                        "type", PRODUCTION_ARTIFACT,
                        "fileName", value.getFileName(),
                        "contentType", value.getContentType())));
    photos.findByResponseId(order(asCase).getResponseId()).stream()
        .filter(value -> value.getStatus() == GoodsSurveyPhotoStatus.CONFIRMED)
        .forEach(
            value ->
                result.add(
                    map(
                        "id", value.getId(),
                        "type", CUSTOMER_PHOTO,
                        "fileName", "고객 사진",
                        "contentType", value.getContentType())));
    return result;
  }

  /** 제작 담당자가 자기에게 열린 사건만 확인한다. 일반 주문 조회 권한은 부여하지 않는다. */
  public List<Map<String, Object>> myCases() {
    var actor = access.current();
    if (actor.getRole() != AdminRole.PRODUCTION || actor.getWorkRoles().isEmpty()) return List.of();
    Instant now = clock.instant();
    var result = new ArrayList<Map<String, Object>>();
    for (var asCase : cases.findAllByOrderByIdDesc()) {
      if (!asCase.isOpen()) continue;
      grants.findByAsCaseIdAndRecipientIdOrderByIdDesc(asCase.getId(), actor.getId()).stream()
          .filter(grant -> grant.isActiveAt(now))
          .findFirst()
          .ifPresent(
              grant ->
                  result.add(
                      map(
                          "id", asCase.getId(),
                          "orderNumber", asCase.getOrderNumber(),
                          "reason", asCase.getReason(),
                          "expiresAt", grant.getExpiresAt(),
                          "assets",
                          grantAssets.findByGrantIdOrderByIdAsc(grant.getId()).stream()
                              .map(asset -> map("id", asset.getAssetId(), "type", asset.getAssetType()))
                              .toList())));
    }
    return result;
  }

  @Transactional
  public Map<String, Object> open(Map<String, Object> input) {
    var actor = owner();
    String orderNumber = required(input, "orderNumber", 20);
    String reason = required(input, "reason", 1000);
    var order =
        orders
            .findByOrderNumber(orderNumber)
            .orElseThrow(() -> notFound("주문을 찾을 수 없습니다."));
    if (!EnumSet.of(GoodsOrderStatus.SHIPPED, GoodsOrderStatus.PICKED_UP).contains(order.getStatus()))
      throw new WorkflowException(409, "ORDER_NOT_COMPLETED", "완료된 주문에서만 AS를 시작할 수 있습니다.");
    var value = cases.saveAndFlush(AsCase.open(orderNumber, reason, actor.getId(), clock.instant()));
    audit(value, "OPEN_AS_CASE", null, null);
    return caseView(value);
  }

  @Transactional
  public Map<String, Object> grant(Long caseId, Map<String, Object> input) {
    var actor = owner();
    var asCase = openCase(caseId);
    Long recipientId = longValue(input, "recipientId");
    if (recipientId == null) throw invalid("담당자를 선택해 주세요.");
    var recipient = accounts.findById(recipientId).orElseThrow(() -> invalid("사용할 수 없는 담당자입니다."));
    if (!recipient.canSignIn()
        || recipient.getRole() != AdminRole.PRODUCTION
        || recipient.getWorkRoles().isEmpty())
      throw invalid("활성 제작 담당자에게만 AS 자료를 열 수 있습니다.");
    List<AssetRef> selected = selectedAssets(asCase, stringList(input, "artifactIds"));
    Long replacedGrantId = longValue(input, "replacesGrantId");
    if (replacedGrantId != null) {
      var previous =
          grants
              .findById(replacedGrantId)
              .filter(g -> g.getAsCaseId().equals(asCase.getId()) && g.getRecipientId().equals(recipientId))
              .orElseThrow(() -> invalid("같은 사건·담당자의 이전 권한만 교체할 수 있습니다."));
      previous.revoke(actor.getId(), clock.instant());
    }
    Instant now = clock.instant();
    var value =
        grants.saveAndFlush(
            AsCaseAccessGrant.activate(asCase.getId(), recipientId, actor.getId(), replacedGrantId, now));
    grantAssets.saveAllAndFlush(
        selected.stream()
            .map(asset -> AsCaseAccessGrantAsset.of(value.getId(), asset.type(), asset.id()))
            .toList());
    audit(asCase, "GRANT_AS_ACCESS", null, String.valueOf(value.getId()));
    return grantView(value);
  }

  @Transactional
  public Map<String, Object> revoke(Long caseId, Long grantId) {
    var actor = owner();
    var asCase = openCase(caseId);
    var grant =
        grants
            .findById(grantId)
            .filter(value -> value.getAsCaseId().equals(asCase.getId()))
            .orElseThrow(() -> notFound("AS 열람 권한을 찾을 수 없습니다."));
    grant.revoke(actor.getId(), clock.instant());
    audit(asCase, "REVOKE_AS_ACCESS", String.valueOf(grantId), null);
    return grantView(grant);
  }

  @Transactional
  public Map<String, Object> close(Long caseId) {
    var actor = owner();
    var asCase = openCase(caseId);
    asCase.close(actor.getId(), clock.instant());
    audit(asCase, "CLOSE_AS_CASE", "OPEN", "CLOSED");
    return caseView(asCase);
  }

  public Map<String, Object> download(Long caseId, String assetId) {
    var asCase = cases.findById(caseId).filter(AsCase::isOpen).orElseThrow(() -> notFound("AS 사건을 찾을 수 없습니다."));
    var actor = access.current();
    if (actor.getRole() != AdminRole.PRODUCTION || actor.getWorkRoles().isEmpty())
      throw notFound("AS 자료를 찾을 수 없습니다.");
    Instant now = clock.instant();
    var grant =
        grants.findByAsCaseIdAndRecipientIdOrderByIdDesc(caseId, actor.getId()).stream()
            .filter(value -> value.isActiveAt(now))
            .filter(value -> !grantAssets.findByGrantIdAndAssetId(value.getId(), assetId).isEmpty())
            .findFirst()
            .orElseThrow(() -> notFound("AS 자료를 찾을 수 없습니다."));
    var allowed = grantAssets.findByGrantIdAndAssetId(grant.getId(), assetId);
    if (allowed.size() != 1) throw notFound("AS 자료를 찾을 수 없습니다.");
    AssetRef asset = loadAsset(asCase, allowed.get(0).getAssetType(), assetId);
    Duration ttl = Duration.between(now, grant.getExpiresAt());
    if (ttl.compareTo(Duration.ofMinutes(5)) > 0) ttl = Duration.ofMinutes(5);
    var link = storage.presignDownload(asset.objectKey(), ttl, now.plus(ttl));
    audit(asCase, "DOWNLOAD_AS_ASSET", null, asset.type() + ":" + asset.id());
    return map("url", link.url(), "expiresAt", link.expiresAt());
  }

  private List<AssetRef> selectedAssets(AsCase asCase, List<String> ids) {
    if (ids.isEmpty()) throw invalid("열람할 자료를 한 개 이상 선택해 주세요.");
    if (new HashSet<>(ids).size() != ids.size()) throw invalid("같은 자료를 두 번 선택할 수 없습니다.");
    return ids.stream().map(id -> loadAnyAsset(asCase, id)).toList();
  }

  private AssetRef loadAnyAsset(AsCase asCase, String id) {
    if (!retained(asCase)) throw invalid("자료 보관 기간이 끝난 주문입니다.");
    var artifact = artifacts.findById(id).filter(ProductionArtifact::isConfirmed);
    var photo = photos.findByIdAndResponseId(id, order(asCase).getResponseId()).filter(p -> p.getStatus() == GoodsSurveyPhotoStatus.CONFIRMED);
    if (artifact.isPresent() && artifact.get().getOrderNumber().equals(asCase.getOrderNumber()) && photo.isEmpty())
      return artifact(artifact.get());
    if (photo.isPresent() && artifact.isEmpty()) return photo(photo.get());
    throw invalid("해당 주문에 보관된 확정 자료만 선택할 수 있습니다.");
  }

  private AssetRef loadAsset(AsCase asCase, String type, String id) {
    if (!retained(asCase)) throw notFound("AS 자료를 찾을 수 없습니다.");
    if (PRODUCTION_ARTIFACT.equals(type)) {
      var artifact =
          artifacts
              .findById(id)
              .filter(ProductionArtifact::isConfirmed)
              .filter(value -> value.getOrderNumber().equals(asCase.getOrderNumber()))
              .orElseThrow(() -> notFound("AS 자료를 찾을 수 없습니다."));
      return artifact(artifact);
    }
    if (CUSTOMER_PHOTO.equals(type)) {
      var photo =
          photos
              .findByIdAndResponseId(id, order(asCase).getResponseId())
              .filter(value -> value.getStatus() == GoodsSurveyPhotoStatus.CONFIRMED)
              .orElseThrow(() -> notFound("AS 자료를 찾을 수 없습니다."));
      return photo(photo);
    }
    throw notFound("AS 자료를 찾을 수 없습니다.");
  }

  private Map<String, Object> caseView(AsCase value) {
    return map(
        "id", value.getId(),
        "orderNumber", value.getOrderNumber(),
        "reason", value.getReason(),
        "status", value.getStatus(),
        "createdAt", value.getCreatedAt(),
        "closedAt", value.getClosedAt(),
        "grants", grants.findByAsCaseIdOrderByIdDesc(value.getId()).stream().map(this::grantView).toList());
  }

  private Map<String, Object> grantView(AsCaseAccessGrant value) {
    return map(
        "id", value.getId(),
        "recipientId", value.getRecipientId(),
        "activatedAt", value.getActivatedAt(),
        "expiresAt", value.getExpiresAt(),
        "revokedAt", value.getRevokedAt(),
        "replacedGrantId", value.getReplacedGrantId(),
        "assets",
        grantAssets.findByGrantIdOrderByIdAsc(value.getId()).stream()
            .map(asset -> map("id", asset.getAssetId(), "type", asset.getAssetType()))
            .toList());
  }

  private AsCase openCase(Long id) {
    return cases.findById(id).filter(AsCase::isOpen).orElseThrow(() -> notFound("열린 AS 사건을 찾을 수 없습니다."));
  }

  private com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment order(AsCase value) {
    return orders.findByOrderNumber(value.getOrderNumber()).orElseThrow(() -> notFound("주문을 찾을 수 없습니다."));
  }

  /** AS 권한은 보관 시계를 멈추거나 이미 지나간 파기 정책을 우회하지 않는다. */
  private boolean retained(AsCase value) {
    var deleteAfter = order(value).getDeleteAfter();
    return deleteAfter == null || deleteAfter.isAfter(clock.instant());
  }

  private com.pawever.backend.admin.entity.AdminAccount owner() {
    var actor = access.current();
    if (actor.getRole() != AdminRole.OWNER)
      throw new WorkflowException(403, "FORBIDDEN", "OWNER만 AS 권한을 관리할 수 있습니다.");
    return actor;
  }

  private void audit(AsCase value, String action, String before, String after) {
    audits.save(
        WorkflowAudit.of("as-case:" + value.getId(), access.current().getId(), action, before, after, null, clock.instant()));
  }

  private static AssetRef artifact(ProductionArtifact value) {
    return new AssetRef(PRODUCTION_ARTIFACT, value.getId(), value.getObjectKey());
  }

  private static AssetRef photo(com.pawever.backend.goodssurvey.entity.GoodsSurveyPhoto value) {
    return new AssetRef(CUSTOMER_PHOTO, value.getId(), value.getObjectKey());
  }

  private static String required(Map<String, Object> input, String key, int max) {
    Object raw = input.get(key);
    String value = raw == null ? "" : String.valueOf(raw).trim();
    if (value.isBlank() || value.length() > max) throw invalid(key + " 값을 확인해 주세요.");
    return value;
  }

  private static Long longValue(Map<String, Object> input, String key) {
    Object value = input.get(key);
    if (value instanceof Number number) return number.longValue();
    try {
      return value == null ? null : Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw invalid(key + " 값을 확인해 주세요.");
    }
  }

  private static List<String> stringList(Map<String, Object> input, String key) {
    if (!(input.get(key) instanceof Collection<?> values)) throw invalid(key + " 값을 확인해 주세요.");
    var result = new ArrayList<String>();
    for (Object value : values) {
      String id = value == null ? "" : String.valueOf(value).trim();
      if (id.isBlank() || id.length() > 80) throw invalid(key + " 값을 확인해 주세요.");
      result.add(id);
    }
    return result;
  }

  private static WorkflowException invalid(String message) {
    return new WorkflowException(400, "INVALID_REQUEST", message);
  }

  private static WorkflowException notFound(String message) {
    return new WorkflowException(404, "NOT_FOUND", message);
  }

  private static Map<String, Object> map(Object... values) {
    var result = new LinkedHashMap<String, Object>();
    for (int i = 0; i < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]);
    return result;
  }

  private record AssetRef(String type, String id, String objectKey) {}
}
