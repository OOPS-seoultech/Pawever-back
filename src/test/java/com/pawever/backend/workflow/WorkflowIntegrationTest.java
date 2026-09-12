package com.pawever.backend.workflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pawever.backend.admin.entity.*;
import com.pawever.backend.admin.repository.AdminAccountRepository;
import com.pawever.backend.admin.security.AdminTokenProvider;
import com.pawever.backend.goodssurvey.entity.*;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.service.GoodsSurveyPhotoStorage;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "admin.jwt-secret=workflow-test-secret-at-least-thirty-two-characters")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired AdminAccountRepository accounts;
  @Autowired GoodsSurveyFulfillmentRepository orders;
  @Autowired com.pawever.backend.goodssurvey.repository.GoodsSurveyCampaignRepository campaigns;
  @Autowired com.pawever.backend.goodssurvey.repository.GoodsSurveyResponseRepository responses;
  @Autowired com.pawever.backend.goodssurvey.repository.GoodsSurveyPhotoRepository photos;
  @Autowired ProductionTaskRepository tasks;
  @Autowired WorkflowSettingsRepository settings;
  @Autowired StaffPermissionOverrideRepository overrides;
  @Autowired AdminTokenProvider tokens;
  @MockitoBean GoodsSurveyPhotoStorage storage;
  String owner, modeler, stranger, number;
  Long modelerId;
  Long reviewerId;
  Long ownerId;

  @org.springframework.test.context.DynamicPropertySource
  static void localMariaDb(org.springframework.test.context.DynamicPropertyRegistry properties) {
    String url = System.getenv("WORKFLOW_JDBC_URL");
    if (url == null) return;
    if (!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/workflow_validation[a-zA-Z0-9_]*"))
      throw new IllegalArgumentException("Use an isolated localhost workflow_validation database");
    properties.add("spring.datasource.url", () -> url);
    properties.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
    properties.add("spring.datasource.username", () -> System.getenv("WORKFLOW_DB_USER"));
    properties.add("spring.datasource.password", () -> System.getenv("WORKFLOW_DB_PASSWORD"));
    properties.add("spring.flyway.enabled", () -> true);
    // Production uses Flyway + none. The baseline funeral_company_images mapping
    // already differs in nullability; exercise all workflow tables by real CRUD here.
    properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
  }

  String account(AdminRole role, Set<WorkRole> roles) {
    var a =
        AdminAccount.invite(
            UUID.randomUUID() + "@example.test",
            "테스트 담당자",
            role,
            "test",
            Instant.now().plusSeconds(3600));
    a.activate("unused-test-hash");
    a.setWorkRoles(roles);
    accounts.saveAndFlush(a);
    if (role == AdminRole.OWNER) ownerId = a.getId();
    if (roles.contains(WorkRole.MODELING)) modelerId = a.getId();
    if (roles.contains(WorkRole.DESIGN_QC)) reviewerId = a.getId();
    return tokens.createToken(a.getId(), role);
  }

  @BeforeEach
  void setup() {
    owner = account(AdminRole.OWNER, Set.of());
    modeler = account(AdminRole.PRODUCTION, Set.of(WorkRole.MODELING));
    stranger = account(AdminRole.PRODUCTION, Set.of(WorkRole.DESIGN_QC));
    number = "PE-" + UUID.randomUUID().toString().substring(0, 12);
    String campaignId = UUID.randomUUID().toString(), responseId = UUID.randomUUID().toString();
    campaigns.saveAndFlush(
        GoodsSurveyCampaign.create(
            campaignId,
            100,
            0,
            Instant.now().minusSeconds(3600),
            Instant.now().plusSeconds(3600),
            true,
            true));
    responses.saveAndFlush(
        GoodsSurveyResponse.draft(
            responseId, campaignId, "v1", UUID.randomUUID().toString(), "figure", "{}"));
    var o =
        GoodsSurveyFulfillment.create(
            responseId,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "{}",
            "figure",
            null,
            "초코",
            "보호자",
            "01012345678",
            UUID.randomUUID().toString(),
            GoodsDeliveryMethod.PICKUP,
            null,
            null,
            null,
            "v1",
            Instant.now(),
            false,
            number,
            GoodsOrderPricing.listPrice(18900, 0),
            false,
            false,
            "v1",
            10080,
            1825);
    orders.saveAndFlush(o);
    for (int i = 0; i < 3; i++) {
      var p =
          GoodsSurveyPhoto.pending(
              UUID.randomUUID().toString(),
              o.getResponseId(),
              "photo-" + i,
              "test/" + UUID.randomUUID(),
              "image/png",
              10,
              Instant.now().plusSeconds(600));
      p.confirm(10, Instant.now());
      photos.saveAndFlush(p);
    }
  }

  @Test
  void staffIdentityComesFromTheActiveAccount() throws Exception {
    mvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + modeler))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.workRoles[0]").value("MODELING"));
    var a = accounts.findById(modelerId).orElseThrow();
    a.disable();
    accounts.saveAndFlush(a);
    mvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + modeler))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void paymentCreatesOneTaskAndForbidsAnotherWorkersAccess() throws Exception {
    mvc.perform(
            patch("/api/admin/workflow/default-assignees")
                .header("Authorization", "Bearer " + owner)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(
                    "{\"modeling\":"
                        + modelerId
                        + ",\"review\":null,\"version\":"
                        + settings.findById(1L).map(WorkflowSettings::getVersion).orElse(0L)
                        + "}"))
        .andExpect(status().isOk());
    String key = UUID.randomUUID().toString();
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post("/api/admin/orders/" + number + "/payments/confirm")
                  .header("Authorization", "Bearer " + owner)
                  .header("Idempotency-Key", key)
                  .contentType("application/json")
                  .content("{\"version\":0,\"amount\":18900,\"memo\":\"은행 대조\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.paymentStatus").value("CONFIRMED"))
          .andExpect(jsonPath("$.data.guardianName").value("보호자"));
    mvc.perform(get("/api/production/my-tasks").header("Authorization", "Bearer " + modeler))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.orderNumber == '" + number + "')]").isNotEmpty());
    mvc.perform(
            get("/api/admin/orders/" + number + "/workflow")
                .header("Authorization", "Bearer " + modeler))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.guardianName").doesNotExist());
    mvc.perform(
            get("/api/admin/orders/" + number + "/workflow")
                .header("Authorization", "Bearer " + stranger))
        .andExpect(status().isNotFound());
  }

  @Test
  void wrongAmountDoesNotConfirmAndProductionCannotConfirm() throws Exception {
    mvc.perform(
            post("/api/admin/orders/" + number + "/payments/confirm")
                .header("Authorization", "Bearer " + modeler)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content("{\"version\":0,\"amount\":18900}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/admin/orders/" + number + "/payments/confirm")
                .header("Authorization", "Bearer " + owner)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content("{\"version\":0,\"amount\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
        .andExpect(
            jsonPath("$.data.blockingIssues")
                .value(org.hamcrest.Matchers.hasItem("PAYMENT_MISMATCH")));
  }

  @Test
  void oldAccountRoutesCannotElevateAdminOrDisableSelf() throws Exception {
    String admin = account(AdminRole.ADMIN, Set.of());
    mvc.perform(
            post("/api/admin/accounts")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(
                    "{\"email\":\"owner-"
                        + UUID.randomUUID()
                        + "@example.test\",\"name\":\"test\",\"role\":\"OWNER\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/admin/accounts/" + ownerId).header("Authorization", "Bearer " + owner))
        .andExpect(status().isForbidden());
  }

  @Test
  void deniedPhotoPermissionAndDependenciesApplyToOldAndNewRoutes() throws Exception {
    var c = settings.findById(1L).orElseGet(WorkflowSettings::new);
    c.change(modelerId, reviewerId);
    settings.saveAndFlush(c);
    var row =
        send(
            owner,
            "/api/admin/orders/" + number + "/payments/confirm",
            "{\"version\":0,\"amount\":18900}",
            UUID.randomUUID().toString());
    overrides.saveAndFlush(
        StaffPermissionOverride.of(
            modelerId, PermissionKey.VIEW_CUSTOMER_PHOTOS, false, null, "test deny"));
    mvc.perform(
            post("/api/admin/orders/" + number + "/photo-links")
                .header("Authorization", "Bearer " + modeler))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/production/tasks/" + row.get("taskId") + "/start")
                .header("Authorization", "Bearer " + modeler)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content("{\"version\":" + row.get("version") + "}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void staleVersionAndChangedIdempotencyBodyAreRejected() throws Exception {
    String key = UUID.randomUUID().toString(),
        path = "/api/admin/orders/" + number + "/payments/confirm";
    send(owner, path, "{\"version\":0,\"amount\":1}", key);
    mvc.perform(
            post(path)
                .header("Authorization", "Bearer " + owner)
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content("{\"version\":0,\"amount\":18900}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    mvc.perform(
            post(path)
                .header("Authorization", "Bearer " + owner)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content("{\"version\":0,\"amount\":18900}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.data.version").value(1));
  }

  @Test
  void legacyStatusCannotSkipEnrolledWorkflowAndDeniedFieldsAreRemoved() throws Exception {
    send(
        owner,
        "/api/admin/orders/" + number + "/payments/confirm",
        "{\"version\":0,\"amount\":18900}",
        UUID.randomUUID().toString());
    mvc.perform(
            post("/api/admin/orders/" + number + "/status")
                .header("Authorization", "Bearer " + owner)
                .contentType("application/json")
                .content("{\"status\":\"IN_PRODUCTION\"}"))
        .andExpect(status().isConflict());
    overrides.saveAndFlush(
        StaffPermissionOverride.of(
            ownerId, PermissionKey.VIEW_CUSTOMER_CONTACT, false, null, "test deny"));
    mvc.perform(get("/api/admin/orders/" + number).header("Authorization", "Bearer " + owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.shipping.phone").doesNotExist());
  }

  @Test
  void simultaneousPaymentConfirmationCreatesExactlyOneTask() throws Exception {
    String key = UUID.randomUUID().toString(),
        path = "/api/admin/orders/" + number + "/payments/confirm",
        body = "{\"version\":0,\"amount\":18900}";
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    var gate = new java.util.concurrent.CountDownLatch(1);
    try {
      var first =
          executor.submit(
              () -> {
                gate.await();
                return send(owner, path, body, key);
              });
      var second =
          executor.submit(
              () -> {
                gate.await();
                return send(owner, path, body, key);
              });
      gate.countDown();
      org.assertj.core.api.Assertions.assertThat(
              first.get(30, java.util.concurrent.TimeUnit.SECONDS))
          .isEqualTo(second.get(30, java.util.concurrent.TimeUnit.SECONDS));
      org.assertj.core.api.Assertions.assertThat(tasks.findByOrderNumberOrderByIdAsc(number))
          .hasSize(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void freeOrdersStayFreeAndUnknownHistoricalProductionIsNotEnrolled() throws Exception {
    var o = orders.findByOrderNumber(number).orElseThrow();
    o.changeStatus(GoodsOrderStatus.LEGACY_FREE);
    org.springframework.test.util.ReflectionTestUtils.setField(o, "paymentAmountKrw", 0);
    o = orders.saveAndFlush(o);
    var row =
        send(
            owner,
            "/api/admin/orders/" + number + "/workflow/enroll",
            "{\"version\":" + o.getVersion() + "}",
            UUID.randomUUID().toString());
    org.assertj.core.api.Assertions.assertThat(row.get("paymentStatus").asText())
        .isEqualTo("NOT_REQUIRED");
    org.assertj.core.api.Assertions.assertThat(
            orders.findByOrderNumber(number).orElseThrow().getPaidAt())
        .isNull();
    // A historical production order has no task/stage. Reading never creates one.
    var old = orders.findByOrderNumber(number).orElseThrow();
    String secondNumber = number.substring(0, number.length() - 1) + "Z";
    var separate =
        GoodsSurveyFulfillment.create(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "{}",
            "figure",
            null,
            "초코",
            "보호자",
            "01012345678",
            UUID.randomUUID().toString(),
            GoodsDeliveryMethod.PICKUP,
            null,
            null,
            null,
            "v1",
            Instant.now(),
            false,
            secondNumber,
            GoodsOrderPricing.listPrice(0, 0),
            false,
            false,
            "v1",
            10080,
            1825);
    responses.saveAndFlush(
        GoodsSurveyResponse.draft(
            separate.getResponseId(),
            responses.findById(old.getResponseId()).orElseThrow().getCampaignId(),
            "v1",
            UUID.randomUUID().toString(),
            "figure",
            "{}"));
    separate.changeStatus(GoodsOrderStatus.IN_PRODUCTION);
    orders.saveAndFlush(separate);
    mvc.perform(
            get("/api/admin/orders/" + secondNumber + "/workflow")
                .header("Authorization", "Bearer " + owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.requiresMigrationReview").value(true))
        .andExpect(jsonPath("$.data.taskId").doesNotExist());
    org.assertj.core.api.Assertions.assertThat(tasks.findByOrderNumberOrderByIdAsc(secondNumber))
        .isEmpty();
  }

  com.fasterxml.jackson.databind.JsonNode send(String token, String path, String body, String key)
      throws Exception {
    String result =
        mvc.perform(
                post(path)
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", key)
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new com.fasterxml.jackson.databind.ObjectMapper().readTree(result).get("data");
  }

  @Test
  void reviewApprovalCreatesOneColorTaskAndPreservesPayment() throws Exception {
    var row = readyForReview();
    String path = reviewPath(row), body = reviewBody(row, "APPROVED", null, "형상 확인 완료");
    String key = UUID.randomUUID().toString();
    var result = send(stranger, path, body, key);
    org.assertj.core.api.Assertions.assertThat(send(stranger, path, body, key)).isEqualTo(result);
    org.assertj.core.api.Assertions.assertThat(result.get("productionStage").asText())
        .isEqualTo("COLOR_MAPPING");
    org.assertj.core.api.Assertions.assertThat(result.get("assignee").get("id").asLong())
        .isEqualTo(reviewerId);
    org.assertj.core.api.Assertions.assertThat(result.get("blockingIssues").size()).isZero();
    org.assertj.core.api.Assertions.assertThat(
            result.get("reviews").get(0).get("decision").asText())
        .isEqualTo("APPROVED");
    org.assertj.core.api.Assertions.assertThat(tasks.findByOrderNumberOrderByIdAsc(number))
        .hasSize(3);
    var order = orders.findByOrderNumber(number).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(order.paymentStatus()).isEqualTo("CONFIRMED");
    org.assertj.core.api.Assertions.assertThat(order.getPaymentAmountKrw()).isEqualTo(18900);
    mvc.perform(get("/api/production/my-tasks").header("Authorization", "Bearer " + stranger))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data[?(@.orderNumber == '" + number + "')].productionStage")
                .value(org.hamcrest.Matchers.contains("COLOR_MAPPING")));
  }

  @Test
  void correctionsKeepHistoryAndRequireNewFilesForEveryAttempt() throws Exception {
    var row = readyForReview();
    for (int attempt = 2; attempt <= 3; attempt++) {
      row =
          send(
              stranger,
              reviewPath(row),
              reviewBody(row, "CHANGES_REQUESTED", "EARS", "귀를 더 둥글게"),
              UUID.randomUUID().toString());
      org.assertj.core.api.Assertions.assertThat(row.get("productionStage").asText())
          .isEqualTo("MODELING_QUEUE");
      org.assertj.core.api.Assertions.assertThat(row.get("taskAttempt").asInt()).isEqualTo(attempt);
      org.assertj.core.api.Assertions.assertThat(row.get("assignee").get("id").asLong())
          .isEqualTo(modelerId);
      org.assertj.core.api.Assertions.assertThat(row.get("reviews").size()).isEqualTo(attempt - 1);
      org.assertj.core.api.Assertions.assertThat(
              row.get("reviews").get(attempt - 2).get("note").asText())
          .isEqualTo("귀를 더 둥글게");
      String taskPath = "/api/production/tasks/" + row.get("taskId");
      row = send(modeler, taskPath + "/start", versionBody(row), UUID.randomUUID().toString());
      mvc.perform(
              post(taskPath + "/complete")
                  .header("Authorization", "Bearer " + modeler)
                  .header("Idempotency-Key", UUID.randomUUID().toString())
                  .contentType("application/json")
                  .content(versionBody(row)))
          .andExpect(status().isUnprocessableEntity());
      seedSubmittedFiles(row.get("taskId").asLong());
      row = send(modeler, taskPath + "/complete", versionBody(row), UUID.randomUUID().toString());
      org.assertj.core.api.Assertions.assertThat(row.get("taskAttempt").asInt()).isEqualTo(attempt);
      org.assertj.core.api.Assertions.assertThat(row.get("blockingIssues").size()).isZero();
    }
    row =
        send(
            stranger,
            reviewPath(row),
            reviewBody(row, "APPROVED", null, "수정 확인"),
            UUID.randomUUID().toString());
    org.assertj.core.api.Assertions.assertThat(row.get("reviews").size()).isEqualTo(3);
    org.assertj.core.api.Assertions.assertThat(row.get("artifacts").size()).isEqualTo(9);
    org.assertj.core.api.Assertions.assertThat(row.get("productionStage").asText())
        .isEqualTo("COLOR_MAPPING");
  }

  @Test
  void reviewRequiresAssignedReviewerCurrentPermissionAndValidReason() throws Exception {
    var row = readyForReview();
    for (String unauthorized :
        List.of(owner, modeler, account(AdminRole.PRODUCTION, Set.of(WorkRole.DESIGN_QC)))) {
      mvc.perform(
              post(reviewPath(row))
                  .header("Authorization", "Bearer " + unauthorized)
                  .header("Idempotency-Key", UUID.randomUUID().toString())
                  .contentType("application/json")
                  .content(reviewBody(row, "APPROVED", null, "")))
          .andExpect(status().isForbidden());
    }
    for (String invalid :
        List.of(
            reviewBody(row, "CHANGES_REQUESTED", null, "귀 수정"),
            reviewBody(row, "CHANGES_REQUESTED", "UNKNOWN", "귀 수정"),
            reviewBody(row, "CHANGES_REQUESTED", "EARS", "   "),
            reviewBody(row, "UNKNOWN", null, ""),
            "{\"version\":" + row.get("version") + ",\"decision\":\"APPROVED\"}")) {
      mvc.perform(
              post(reviewPath(row))
                  .header("Authorization", "Bearer " + stranger)
                  .header("Idempotency-Key", UUID.randomUUID().toString())
                  .contentType("application/json")
                  .content(invalid))
          .andExpect(status().isBadRequest());
    }
    Long assignedReviewer =
        tasks.findById(row.get("taskId").asLong()).orElseThrow().getAssigneeId();
    overrides.saveAndFlush(
        StaffPermissionOverride.of(
            assignedReviewer, PermissionKey.VIEW_PRODUCTION_FILES, false, null, "test deny"));
    mvc.perform(
            post(reviewPath(row))
                .header("Authorization", "Bearer " + stranger)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(reviewBody(row, "APPROVED", null, "")))
        .andExpect(status().isForbidden());
  }

  @Test
  void unavailableOriginalModelerLeavesCorrectionUnassigned() throws Exception {
    var row = readyForReview();
    var original = accounts.findById(modelerId).orElseThrow();
    original.disable();
    accounts.saveAndFlush(original);
    row =
        send(
            stranger,
            reviewPath(row),
            reviewBody(row, "CHANGES_REQUESTED", "SHAPE", "얼굴 형태 수정"),
            UUID.randomUUID().toString());
    org.assertj.core.api.Assertions.assertThat(row.get("assignee").isNull()).isTrue();
    org.assertj.core.api.Assertions.assertThat(row.get("blockingIssues").toString())
        .contains("UNASSIGNED");
  }

  @Test
  void competingReviewDecisionsCannotBothAdvanceTheOrder() throws Exception {
    var row = readyForReview();
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    var gate = new java.util.concurrent.CountDownLatch(1);
    try {
      var results = new ArrayList<java.util.concurrent.Future<Integer>>();
      for (String decision : List.of("APPROVED", "CHANGES_REQUESTED")) {
        results.add(
            executor.submit(
                () -> {
                  gate.await();
                  return mvc.perform(
                          post(reviewPath(row))
                              .header("Authorization", "Bearer " + stranger)
                              .header("Idempotency-Key", UUID.randomUUID().toString())
                              .contentType("application/json")
                              .content(
                                  reviewBody(
                                      row,
                                      decision,
                                      decision.equals("APPROVED") ? null : "EARS",
                                      "확인")))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      gate.countDown();
      org.assertj.core.api.Assertions.assertThat(
              List.of(
                  results.get(0).get(30, java.util.concurrent.TimeUnit.SECONDS),
                  results.get(1).get(30, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
      org.assertj.core.api.Assertions.assertThat(tasks.findByOrderNumberOrderByIdAsc(number))
          .hasSize(3);
    } finally {
      executor.shutdownNow();
    }
  }

  @Autowired ProductionArtifactRepository artifactRecords;

  @Test
  void reviewChecksSubmittedFilesAndKeepsFullLengthCorrectionNote() throws Exception {
    var row = readyForReview();
    var sourceFile = artifactRecords.findByOrderNumberOrderByIdAsc(number).get(0);
    org.springframework.test.util.ReflectionTestUtils.setField(sourceFile, "confirmed", false);
    artifactRecords.saveAndFlush(sourceFile);
    mvc.perform(
            post(reviewPath(row))
                .header("Authorization", "Bearer " + stranger)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(reviewBody(row, "APPROVED", null, "")))
        .andExpect(status().isUnprocessableEntity());
    sourceFile.confirm();
    artifactRecords.saveAndFlush(sourceFile);
    String key = UUID.randomUUID().toString();
    var result =
        send(
            stranger,
            reviewPath(row),
            reviewBody(row, "CHANGES_REQUESTED", "EARS", "수".repeat(300)),
            key);
    org.assertj.core.api.Assertions.assertThat(result.get("reviews").get(0).get("note").asText())
        .hasSize(300);
    mvc.perform(
            post(reviewPath(row))
                .header("Authorization", "Bearer " + stranger)
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(reviewBody(row, "APPROVED", null, "")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
  }

  com.fasterxml.jackson.databind.JsonNode readyForReview() throws Exception {
    var c = settings.findById(1L).orElseGet(WorkflowSettings::new);
    c.change(modelerId, reviewerId);
    settings.saveAndFlush(c);
    var row =
        send(
            owner,
            "/api/admin/orders/" + number + "/payments/confirm",
            "{\"version\":0,\"amount\":18900}",
            UUID.randomUUID().toString());
    String path = "/api/production/tasks/" + row.get("taskId");
    row = send(modeler, path + "/start", versionBody(row), UUID.randomUUID().toString());
    seedSubmittedFiles(row.get("taskId").asLong());
    return send(modeler, path + "/complete", versionBody(row), UUID.randomUUID().toString());
  }

  void seedSubmittedFiles(Long taskId) {
    for (String kind : List.of("MULTIVIEW", "MODEL_SOURCE", "PRINT_MODEL")) {
      var artifact =
          ProductionArtifact.pending(
              UUID.randomUUID().toString(),
              number,
              taskId,
              modelerId,
              kind,
              kind.equals("MULTIVIEW") ? "view.png" : "model.stl",
              kind.equals("MULTIVIEW") ? "image/png" : "application/octet-stream",
              10,
              Instant.now().plusSeconds(600));
      artifact.confirm();
      artifactRecords.saveAndFlush(artifact);
    }
  }

  String versionBody(com.fasterxml.jackson.databind.JsonNode row) {
    return "{\"version\":" + row.get("version") + "}";
  }

  String reviewPath(com.fasterxml.jackson.databind.JsonNode row) {
    return "/api/production/tasks/" + row.get("taskId") + "/review";
  }

  String reviewBody(
      com.fasterxml.jackson.databind.JsonNode row, String decision, String reason, String note)
      throws Exception {
    var body = new LinkedHashMap<String, Object>();
    body.put("version", row.get("version").asLong());
    body.put("decision", decision);
    body.put("reasonCode", reason);
    body.put("note", note);
    if (decision.equals("APPROVED"))
      body.put("checks", List.of("LIKENESS", "FEATURES", "BASE_CUT", "PRINTABILITY"));
    return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
  }

  @Test
  void modelingFilesAreRequiredAndCompletionHandsOffExactlyOnce() throws Exception {
    var c = settings.findById(1L).orElseGet(WorkflowSettings::new);
    c.change(modelerId, reviewerId);
    settings.saveAndFlush(c);
    var row =
        send(
            owner,
            "/api/admin/orders/" + number + "/payments/confirm",
            "{\"version\":0,\"amount\":18900}",
            UUID.randomUUID().toString());
    long taskId = row.get("taskId").asLong();
    row =
        send(
            modeler,
            "/api/production/tasks/" + taskId + "/start",
            "{\"version\":" + row.get("version") + "}",
            UUID.randomUUID().toString());
    mvc.perform(
            post("/api/production/tasks/" + taskId + "/complete")
                .header("Authorization", "Bearer " + modeler)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content("{\"version\":" + row.get("version") + "}"))
        .andExpect(status().isUnprocessableEntity());
    org.mockito.Mockito.when(
            storage.presignUpload(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new GoodsSurveyPhotoStorage.PresignedUpload(
                "https://storage.example.test/upload", Map.of(), Instant.now().plusSeconds(600)));
    for (String kind : List.of("MULTIVIEW", "MODEL_SOURCE", "PRINT_MODEL")) {
      String type = kind.equals("MULTIVIEW") ? "image/png" : "application/octet-stream";
      String file = kind.equals("MULTIVIEW") ? "view.png" : "model.stl";
      var pending =
          send(
              modeler,
              "/api/production/tasks/" + taskId + "/artifacts/upload-requests",
              "{\"version\":"
                  + row.get("version")
                  + ",\"kind\":\""
                  + kind
                  + "\",\"fileName\":\""
                  + file
                  + "\",\"contentType\":\""
                  + type
                  + "\",\"size\":10}",
              UUID.randomUUID().toString());
      org.mockito.Mockito.when(storage.head(org.mockito.ArgumentMatchers.anyString()))
          .thenReturn(
              new GoodsSurveyPhotoStorage.StoredObject(
                  10, type, new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}));
      row =
          send(
              modeler,
              "/api/production/artifacts/" + pending.get("artifactId").asText() + "/confirm",
              "{\"version\":" + pending.get("version") + "}",
              UUID.randomUUID().toString());
    }
    String key = UUID.randomUUID().toString(), body = "{\"version\":" + row.get("version") + "}";
    for (int i = 0; i < 2; i++)
      send(modeler, "/api/production/tasks/" + taskId + "/complete", body, key);
    mvc.perform(get("/api/production/my-tasks").header("Authorization", "Bearer " + stranger))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data[?(@.orderNumber == '" + number + "')].productionStage")
                .value(org.hamcrest.Matchers.contains("MODEL_REVIEW")));
    org.assertj.core.api.Assertions.assertThat(tasks.findByOrderNumberOrderByIdAsc(number))
        .hasSize(2);
    org.mockito.Mockito.when(
            storage.presignDownload(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new GoodsSurveyPhotoStorage.PresignedDownload(
                "https://storage.example.test/private-file", Instant.now().plusSeconds(300)));
    mvc.perform(get("/api/admin/orders/" + number).header("Authorization", "Bearer " + stranger))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.shipping").doesNotExist())
        .andExpect(jsonPath("$.data.payment").doesNotExist());
    mvc.perform(
            post("/api/admin/orders/" + number + "/photo-links")
                .header("Authorization", "Bearer " + stranger))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.photos.length()").value(3));
    String artifactId = row.get("artifacts").get(0).get("id").asText();
    mvc.perform(
            post("/api/production/artifacts/" + artifactId + "/download-link")
                .header("Authorization", "Bearer " + stranger))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    String unrelated = account(AdminRole.PRODUCTION, Set.of(WorkRole.DESIGN_QC));
    mvc.perform(
            post("/api/production/artifacts/" + artifactId + "/download-link")
                .header("Authorization", "Bearer " + unrelated))
        .andExpect(status().isNotFound());
  }
}
