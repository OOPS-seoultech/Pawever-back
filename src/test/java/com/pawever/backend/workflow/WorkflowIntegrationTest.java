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

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  ShipmentWorkbook shipmentWorkbook;

  @Autowired ShipmentExportBatchRepository shipmentBatches;
  @Autowired ShipmentExportItemRepository shipmentItems;
  @Autowired ShipmentExportService shipmentExports;

  @Autowired
  com.pawever.backend.goodssurvey.service.GoodsSurveyFulfillmentOpsService fulfillmentOps;

  String owner, modeler, stranger, number;

  @Test
  void shippingPackingCannotStartRetentionThroughLegacyInternalDeliveryEndpoint() throws Exception {
    readyForPacking(true);
    var o = orders.findByOrderNumber(number).orElseThrow();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> fulfillmentOps.markDeliveryCompleted(o.getResponseId()))
        .isInstanceOf(WorkflowException.class);
    org.assertj.core.api.Assertions.assertThat(
            orders.findByOrderNumber(number).orElseThrow().getDeleteAfter())
        .isNull();
  }

  @Test
  void shippingPackingFileFailureRollsBackAndCanRetryTheSameKey() throws Exception {
    var row = readyForPacking(true);
    long count = shipmentBatches.count();
    String body = exportBody(row), key = UUID.randomUUID().toString();
    org.mockito.Mockito.doThrow(new WorkflowException(503, "EXPORT_FAILED", "파일 생성 실패"))
        .when(shipmentWorkbook)
        .create(org.mockito.ArgumentMatchers.anyList());
    mvc.perform(
            post("/api/admin/shipments/export-batches")
                .header("Authorization", "Bearer " + owner)
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(body))
        .andExpect(status().isServiceUnavailable());
    org.assertj.core.api.Assertions.assertThat(shipmentBatches.count()).isEqualTo(count);
    org.assertj.core.api.Assertions.assertThat(
            orders.findByOrderNumber(number).orElseThrow().getProductionStage())
        .isEqualTo(ProductionStage.PACKING);
    org.mockito.Mockito.reset(shipmentWorkbook);
    send(owner, "/api/admin/shipments/export-batches", body, key);
  }

  @Test
  void shippingPackingConcurrencyExportsAnOrderOnlyOnce() throws Exception {
    var row = readyForPacking(true);
    String body = exportBody(row), token = owner;
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var results =
          executor.invokeAll(
              List.of(
                  (java.util.concurrent.Callable<Integer>)
                      () ->
                          mvc.perform(
                                  post("/api/admin/shipments/export-batches")
                                      .header("Authorization", "Bearer " + token)
                                      .header("Idempotency-Key", UUID.randomUUID().toString())
                                      .contentType("application/json")
                                      .content(body))
                              .andReturn()
                              .getResponse()
                              .getStatus(),
                  () ->
                      mvc.perform(
                              post("/api/admin/shipments/export-batches")
                                  .header("Authorization", "Bearer " + token)
                                  .header("Idempotency-Key", UUID.randomUUID().toString())
                                  .contentType("application/json")
                                  .content(body))
                          .andReturn()
                          .getResponse()
                          .getStatus()));
      org.assertj.core.api.Assertions.assertThat(
              List.of(results.get(0).get(), results.get(1).get()))
          .containsExactlyInAnyOrder(200, 409);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void shippingPackingSnapshotExpiresEvenAfterOriginalAddressWasStripped() throws Exception {
    var row = readyForPacking(true);
    var batch =
        send(
            owner,
            "/api/admin/shipments/export-batches",
            exportBody(row),
            UUID.randomUUID().toString());
    long id = batch.get("id").asLong();
    var o = orders.findByOrderNumber(number).orElseThrow();
    o.markDeliveryCompleted(Instant.now().minusSeconds(91L * 86400), 90);
    o.stripDeliveryDetails();
    orders.saveAndFlush(o);
    mvc.perform(
            get("/api/admin/shipments/export-batches/" + id + "/file")
                .header("Authorization", "Bearer " + owner))
        .andExpect(status().isGone());
    shipmentExports.purge(Instant.now());
    org.assertj.core.api.Assertions.assertThat(
            shipmentBatches.findById(id).orElseThrow().getFileBase64())
        .isNull();
    org.assertj.core.api.Assertions.assertThat(
            shipmentItems.findByBatchIdOrderByRowNumberAsc(id).get(0).getSnapshotJson())
        .isNull();
  }

  @Test
  void shippingPackingDoesNotExposeOtherOrdersOrAcceptStaleSelections() throws Exception {
    var row = readyForPacking(true);
    String body = exportBody(row);
    expectAs(modeler, "/api/admin/shipments/export-batches", body, 403);
    mvc.perform(get("/api/admin/shipments/candidates").header("Authorization", "Bearer " + modeler))
        .andExpect(status().isForbidden());
    var o = orders.findByOrderNumber(number).orElseThrow();
    o.touchWorkflow(Instant.now());
    orders.saveAndFlush(o);
    expectAs(owner, "/api/admin/shipments/export-batches", body, 409);
    org.assertj.core.api.Assertions.assertThat(shipmentItems.existsByOrderNumber(number)).isFalse();
  }

  @Test
  void shippingPackingCreatesOneTextWorkbookAndReplayUsesTheSameFile() throws Exception {
    var row = readyForPacking(true);
    String body = exportBody(row), key = UUID.randomUUID().toString();
    var batch = send(owner, "/api/admin/shipments/export-batches", body, key);
    org.assertj.core.api.Assertions.assertThat(
            send(owner, "/api/admin/shipments/export-batches", body, key))
        .isEqualTo(batch);
    String file = "/api/admin/shipments/export-batches/" + batch.get("id") + "/file";
    byte[] bytes =
        mvc.perform(get(file).header("Authorization", "Bearer " + owner))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    org.assertj.core.api.Assertions.assertThat(
            mvc.perform(get(file).header("Authorization", "Bearer " + owner))
                .andReturn()
                .getResponse()
                .getContentAsByteArray())
        .isEqualTo(bytes);
    try (var workbook =
        new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
      org.assertj.core.api.Assertions.assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
      var sheet = workbook.getSheetAt(0);
      org.assertj.core.api.Assertions.assertThat(sheet.getSheetName()).isEqualTo("준등기");
      org.assertj.core.api.Assertions.assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1);
      org.assertj.core.api.Assertions.assertThat(sheet.getRow(0).getLastCellNum())
          .isEqualTo((short) 6);
      var expected = List.of(" 보호자 ", "01234", "서울시 테스트로", "101호", "01012345678", "=초코");
      for (int i = 0; i < 6; i++) {
        org.assertj.core.api.Assertions.assertThat(sheet.getRow(0).getCell(i).getCellType())
            .isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
        org.assertj.core.api.Assertions.assertThat(sheet.getRow(0).getCell(i).getStringCellValue())
            .isEqualTo(expected.get(i));
      }
    }
    var o = orders.findByOrderNumber(number).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(o.getProductionStage())
        .isEqualTo(ProductionStage.COMPLETE);
    org.assertj.core.api.Assertions.assertThat(o.shipmentStatus())
        .isEqualTo("AWAITING_POST_OFFICE_RESULT");
    org.assertj.core.api.Assertions.assertThat(o.orderStatus()).isEqualTo("ACTIVE");
    org.assertj.core.api.Assertions.assertThat(o.getDeleteAfter()).isNull();
    expectAs(
        owner,
        "/api/admin/shipments/export-batches",
        exportBody(readJson(owner, "/api/admin/orders/" + number + "/workflow")),
        422);
    Long id = readJson(owner, "/api/admin/me").get("id").asLong();
    overrides.saveAndFlush(
        StaffPermissionOverride.of(id, PermissionKey.VIEW_CUSTOMER_ADDRESS, false, null, "회수"));
    expectAs(owner, "/api/admin/shipments/export-batches", body, 403);
    mvc.perform(get(file).header("Authorization", "Bearer " + owner))
        .andExpect(status().isForbidden());
  }

  @Test
  void shippingPackingRejectsWholeSelectionWhenOneAddressIsInvalid() throws Exception {
    var first = readyForPacking(true);
    String firstNumber = number, firstOwner = owner;
    setup();
    var second = readyForPacking(true);
    var o = orders.findByOrderNumber(number).orElseThrow();
    org.springframework.test.util.ReflectionTestUtils.setField(o, "postalCode", "1234");
    orders.saveAndFlush(o);
    second = readJson(owner, "/api/admin/orders/" + number + "/workflow");
    expectAs(
        firstOwner,
        "/api/admin/shipments/export-batches",
        jsonBody(
            Map.of(
                "orders",
                List.of(
                    Map.of("orderNumber", firstNumber, "version", first.get("version").asLong()),
                    Map.of("orderNumber", number, "version", second.get("version").asLong())))),
        422);
    for (String n : List.of(firstNumber, number)) {
      var current = orders.findByOrderNumber(n).orElseThrow();
      org.assertj.core.api.Assertions.assertThat(current.getProductionStage())
          .isEqualTo(ProductionStage.PACKING);
      org.assertj.core.api.Assertions.assertThat(current.shipmentStatus()).isEqualTo("NOT_READY");
    }
  }

  @Test
  void workflowOrdersCannotBypassPackingWithLegacyShipmentActions() throws Exception {
    readyForPacking(false);
    expectAs(owner, "/api/admin/orders/" + number + "/pickup-complete", "{}", 409);
    expectAs(
        owner,
        "/api/admin/orders/" + number + "/tracking",
        "{\"trackingCompany\":\"우체국\",\"trackingNumber\":\"1234567890123\"}",
        409);
    org.assertj.core.api.Assertions.assertThat(
            orders.findByOrderNumber(number).orElseThrow().shipmentStatus())
        .isEqualTo("NOT_READY");
  }

  com.fasterxml.jackson.databind.JsonNode readyForPacking(boolean shipping) throws Exception {
    var row = printedPlate().get("orders").get(0);
    row =
        send(
            printerToken,
            "/api/production/tasks/" + row.get("taskId") + "/post-processing",
            postBody(row),
            UUID.randomUUID().toString());
    row =
        send(
            printerToken,
            "/api/production/tasks/" + row.get("taskId") + "/quality-check",
            qcBody(row),
            UUID.randomUUID().toString());
    if (shipping) {
      var o = orders.findByOrderNumber(number).orElseThrow();
      var fields =
          Map.of(
              "deliveryMethod",
              GoodsDeliveryMethod.SHIPPING,
              "guardianName",
              " 보호자 ",
              "petName",
              "=초코",
              "postalCode",
              "01234",
              "address",
              "서울시 테스트로",
              "addressDetail",
              "101호",
              "phone",
              "010-1234-5678");
      fields.forEach(
          (name, value) ->
              org.springframework.test.util.ReflectionTestUtils.setField(o, name, value));
      orders.saveAndFlush(o);
    }
    return readJson(owner, "/api/admin/orders/" + number + "/workflow");
  }

  String exportBody(com.fasterxml.jackson.databind.JsonNode row) throws Exception {
    return jsonBody(
        Map.of(
            "orders",
            List.of(
                Map.of(
                    "orderNumber",
                    row.get("orderNumber").asText(),
                    "version",
                    row.get("version").asLong()))));
  }

  void enableCompensationFor(Long workerId) throws Exception {
    var config = readJson(owner, "/api/admin/production-compensation");
    send(
        owner,
        "/api/admin/production-compensation",
        jsonBody(
            Map.of(
                "version",
                config.get("version").asLong(),
                "enabled",
                true,
                "paidWorkerIds",
                List.of(workerId))),
        UUID.randomUUID().toString());
  }

  long settlementCount(String orderNumber) throws Exception {
    long count = 0;
    for (var entry : readJson(owner, "/api/admin/production-settlements"))
      if (entry.get("orderNumber").asText().equals(orderNumber)) count++;
    return count;
  }

  @Test
  void shippingSettlementStartsAtPostOfficeAcceptanceOnlyOnce() throws Exception {
    var row = readyForPacking(true);
    enableCompensationFor(readJson(printerToken, "/api/admin/me").get("id").asLong());
    var batch =
        send(
            owner,
            "/api/admin/shipments/export-batches",
            exportBody(row),
            UUID.randomUUID().toString());
    org.assertj.core.api.Assertions.assertThat(settlementCount(number)).isZero();

    String previewBody =
        jsonBody(
            Map.of(
                "outboundBatchId",
                batch.get("id").asLong(),
                "text",
                "1234567890123 1,800 01234 보호자 =초코\n통상 반송불요 20g"));
    var previewResponse =
        mvc.perform(
                post("/api/admin/postal-imports/preview")
                    .header("Authorization", "Bearer " + owner)
                    .contentType("application/json")
                    .content(previewBody))
            .andExpect(status().isOk())
            .andReturn();
    var preview =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(previewResponse.getResponse().getContentAsString());
    var importRow = preview.get("rows").get(0);
    org.assertj.core.api.Assertions.assertThat(importRow.get("status").asText())
        .isEqualTo("AUTO_MATCH");

    String commitPath = "/api/admin/postal-imports/" + preview.get("batchId").asLong() + "/commit";
    String commitBody = jsonBody(Map.of("selectedRowIds", List.of(importRow.get("id").asLong())));
    mvc.perform(
            post(commitPath)
                .header("Authorization", "Bearer " + owner)
                .contentType("application/json")
                .content(commitBody))
        .andExpect(status().isOk());
    org.assertj.core.api.Assertions.assertThat(settlementCount(number)).isEqualTo(1);

    mvc.perform(
            post(commitPath)
                .header("Authorization", "Bearer " + owner)
                .contentType("application/json")
                .content(commitBody))
        .andExpect(status().isOk());
    org.assertj.core.api.Assertions.assertThat(settlementCount(number)).isEqualTo(1);
  }

  @Test
  void pickupPackingCompletesWorkAndAccruesBeforeSeparateHandoff() throws Exception {
    var row = readyForPacking(false);
    enableCompensationFor(readJson(printerToken, "/api/admin/me").get("id").asLong());
    String body = exportBody(row), key = UUID.randomUUID().toString();

    var completed = send(owner, "/api/admin/shipments/pickup-completions", body, key);
    org.assertj.core.api.Assertions.assertThat(completed.get("completed").asInt()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(
            send(owner, "/api/admin/shipments/pickup-completions", body, key))
        .isEqualTo(completed);

    var packed = orders.findByOrderNumber(number).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(packed.getProductionStage())
        .isEqualTo(ProductionStage.COMPLETE);
    org.assertj.core.api.Assertions.assertThat(packed.shipmentStatus())
        .isEqualTo("READY_FOR_PICKUP");
    org.assertj.core.api.Assertions.assertThat(packed.orderStatus()).isEqualTo("ACTIVE");
    org.assertj.core.api.Assertions.assertThat(packed.getDeleteAfter()).isNull();
    org.assertj.core.api.Assertions.assertThat(settlementCount(number)).isEqualTo(1);

    send(
        owner,
        "/api/admin/orders/" + number + "/pickup-complete",
        "{}",
        UUID.randomUUID().toString());
    var handedOff = orders.findByOrderNumber(number).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(handedOff.shipmentStatus()).isEqualTo("PICKED_UP");
    org.assertj.core.api.Assertions.assertThat(handedOff.getDeleteAfter()).isNotNull();
    org.assertj.core.api.Assertions.assertThat(settlementCount(number)).isEqualTo(1);
  }

  @Test
  void adminCanReleaseConfirmedPlateForNewConfigurationBeforePrinting() throws Exception {
    var plate = confirmedPlate(List.of(readyForPlate()));
    String path = "/api/production/print-batches/" + plate.get("id");
    var input =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(confirmation(plate), java.util.LinkedHashMap.class);
    input.put("note", "프린터 변경을 위한 재구성");
    expectAs(printerToken, path + "/cancel-queued", jsonBody(input), 403);
    send(owner, path + "/cancel-queued", jsonBody(input), UUID.randomUUID().toString());
    var row = readJson(stranger, "/api/admin/orders/" + number + "/workflow");
    org.assertj.core.api.Assertions.assertThat(row.get("productionStage").asText())
        .isEqualTo("PLATE_PREPARATION");
    org.assertj.core.api.Assertions.assertThat(row.get("taskAttempt").asInt()).isEqualTo(2);
    var again = confirmedPlate(List.of(row));
    org.assertj.core.api.Assertions.assertThat(again.get("status").asText()).isEqualTo("CONFIRMED");
  }

  @Test
  void finishingImmediatelyHonorsRevokedFilePermission() throws Exception {
    var row = printedPlate().get("orders").get(0);
    Long id = readJson(printerToken, "/api/admin/me").get("id").asLong();
    overrides.saveAndFlush(
        StaffPermissionOverride.of(id, PermissionKey.VIEW_PRODUCTION_FILES, false, null, "작업 회수"));
    expectAs(
        printerToken,
        "/api/production/tasks/" + row.get("taskId") + "/post-processing",
        postBody(row),
        403);
  }

  @Test
  void compensationTogglePreservesSelectedPaidWorkers() throws Exception {
    var worker = account(AdminRole.PRODUCTION, Set.of(WorkRole.PRINT_FINISHING));
    Long id = readJson(worker, "/api/admin/me").get("id").asLong();
    var config = readJson(owner, "/api/admin/production-compensation");
    config =
        send(
            owner,
            "/api/admin/production-compensation",
            jsonBody(
                Map.of(
                    "version",
                    config.get("version").asLong(),
                    "enabled",
                    true,
                    "paidWorkerIds",
                    List.of(id))),
            UUID.randomUUID().toString());
    config =
        send(
            owner,
            "/api/admin/production-compensation",
            jsonBody(
                Map.of(
                    "version",
                    config.get("version").asLong(),
                    "enabled",
                    false,
                    "paidWorkerIds",
                    List.of(id))),
            UUID.randomUUID().toString());
    config = readJson(owner, "/api/admin/production-compensation");
    org.assertj.core.api.Assertions.assertThat(config.get("paidWorkerIds").size()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(config.get("paidWorkerIds").get(0).asLong())
        .isEqualTo(id);
  }

  @Test
  void qualityPassWaitsForFulfillmentBeforeSettlement() throws Exception {
    var plate = printedPlate();
    var row = plate.get("orders").get(0);
    var config = readJson(owner, "/api/admin/production-compensation");
    send(
        owner,
        "/api/admin/production-compensation",
        jsonBody(
            Map.of(
                "version",
                config.get("version").asLong(),
                "enabled",
                true,
                "paidWorkerIds",
                List.of(plate.get("printingAssigneeId").asLong()))),
        UUID.randomUUID().toString());

    row =
        send(
            printerToken,
            "/api/production/tasks/" + row.get("taskId") + "/post-processing",
            postBody(row),
            UUID.randomUUID().toString());
    row =
        send(
            printerToken,
            "/api/production/tasks/" + row.get("taskId") + "/quality-check",
            qcBody(row),
            UUID.randomUUID().toString());

    org.assertj.core.api.Assertions.assertThat(row.get("productionStage").asText())
        .isEqualTo("PACKING");
    org.assertj.core.api.Assertions.assertThat(readJson(owner, "/api/admin/production-settlements"))
        .noneMatch(entry -> entry.get("orderNumber").asText().equals(number));
  }

  @Test
  void printingCanTransferInProgressAndRevokedWorkerCannotFinish() throws Exception {
    var plate = confirmedPlate(List.of(readyForPlate()));
    String path = "/api/production/print-batches/" + plate.get("id");
    plate = send(printerToken, path + "/start", confirmation(plate), UUID.randomUUID().toString());
    String oldWorker = printerToken;
    String replacement = account(AdminRole.PRODUCTION, Set.of(WorkRole.PRINT_FINISHING));
    Long replacementId = readJson(replacement, "/api/admin/me").get("id").asLong();
    var body = plateConfig(plate.get("orders"), replacementId);
    body.put("version", plate.get("version").asLong());
    plate = send(owner, path + "/assign", jsonBody(body), UUID.randomUUID().toString());
    expectAs(oldWorker, path + "/finish", finishBody(plate, null), 403);
    overrides.saveAndFlush(
        StaffPermissionOverride.of(
            replacementId, PermissionKey.MANAGE_PRINT_BATCH, false, null, "검사"));
    expectAs(replacement, path + "/finish", finishBody(plate, null), 403);
  }

  @Test
  void canceledOrderDuringPrintIsRecordedWithoutProductionHandoff() throws Exception {
    var plate = confirmedPlate(List.of(readyForPlate()));
    String path = "/api/production/print-batches/" + plate.get("id");
    plate = send(printerToken, path + "/start", confirmation(plate), UUID.randomUUID().toString());
    var o = orders.findByOrderNumber(number).orElseThrow();
    o.changeStatus(GoodsOrderStatus.CANCELED);
    orders.saveAndFlush(o);
    expectAs(printerToken, path + "/finish", finishBody(plate, null), 409);
    plate = readJson(printerToken, path);
    plate =
        send(printerToken, path + "/finish", finishBody(plate, null), UUID.randomUUID().toString());
    org.assertj.core.api.Assertions.assertThat(plate.get("results").get(0).get("result").asText())
        .isEqualTo("SKIPPED");
    org.assertj.core.api.Assertions.assertThat(
            plate.get("orders").get(0).get("orderStatus").asText())
        .isEqualTo("CANCELED");
    org.assertj.core.api.Assertions.assertThat(
            tasks.findByOrderNumberOrderByIdAsc(number).stream()
                .filter(t -> t.getStage() == ProductionStage.POST_PROCESSING)
                .count())
        .isZero();
  }

  @Test
  void disabledOrUnpaidWorkersNeverAccrueSettlement() throws Exception {
    for (boolean enabled : List.of(false, true)) {
      var config = readJson(owner, "/api/admin/production-compensation");
      send(
          owner,
          "/api/admin/production-compensation",
          jsonBody(
              Map.of(
                  "version",
                  config.get("version").asLong(),
                  "enabled",
                  enabled,
                  "paidWorkerIds",
                  List.of())),
          UUID.randomUUID().toString());
      var row = printedPlate().get("orders").get(0);
      row =
          send(
              printerToken,
              "/api/production/tasks/" + row.get("taskId") + "/post-processing",
              postBody(row),
              UUID.randomUUID().toString());
      row =
          send(
              printerToken,
              "/api/production/tasks/" + row.get("taskId") + "/quality-check",
              qcBody(row),
              UUID.randomUUID().toString());
      var ledger = readJson(owner, "/api/admin/production-settlements");
      for (var entry : ledger)
        org.assertj.core.api.Assertions.assertThat(entry.get("orderNumber").asText())
            .isNotEqualTo(row.get("orderNumber").asText());
      setup();
    }
  }

  @Test
  void concurrentQcRequestsCreateOnePackingTaskWithoutEarlySettlement() throws Exception {
    var plate = printedPlate();
    var row = plate.get("orders").get(0);
    var config = readJson(owner, "/api/admin/production-compensation");
    send(
        owner,
        "/api/admin/production-compensation",
        jsonBody(
            Map.of(
                "version",
                config.get("version").asLong(),
                "enabled",
                true,
                "paidWorkerIds",
                List.of(plate.get("printingAssigneeId").asLong()))),
        UUID.randomUUID().toString());
    row =
        send(
            printerToken,
            "/api/production/tasks/" + row.get("taskId") + "/post-processing",
            postBody(row),
            UUID.randomUUID().toString());
    String path = "/api/production/tasks/" + row.get("taskId") + "/quality-check",
        body = qcBody(row),
        worker = printerToken;
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    var gate = new java.util.concurrent.CountDownLatch(1);
    try {
      java.util.concurrent.Callable<Integer> action =
          () -> {
            gate.await();
            return mvc.perform(
                    post(path)
                        .header("Authorization", "Bearer " + worker)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var a = pool.submit(action);
      var b = pool.submit(action);
      gate.countDown();
      org.assertj.core.api.Assertions.assertThat(
              List.of(
                  a.get(25, java.util.concurrent.TimeUnit.SECONDS),
                  b.get(25, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    } finally {
      pool.shutdownNow();
    }
    org.assertj.core.api.Assertions.assertThat(
            tasks.findByOrderNumberOrderByIdAsc(number).stream()
                .filter(t -> t.getStage() == ProductionStage.PACKING)
                .count())
        .isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(settlementCount(number)).isZero();
  }

  @Test
  void qcModelCorrectionUsesFreshAttemptAfterEarlierPrintFailure() throws Exception {
    var plate = printedPlate();
    var row = plate.get("orders").get(0);
    row =
        send(
            printerToken,
            "/api/production/tasks/" + row.get("taskId") + "/post-processing",
            postBody(row),
            UUID.randomUUID().toString());
    row =
        send(
            printerToken,
            "/api/production/tasks/" + row.get("taskId") + "/quality-check",
            jsonBody(
                Map.of(
                    "version",
                    row.get("version").asLong(),
                    "decision",
                    "FAILED",
                    "reasonCode",
                    "SHAPE",
                    "reworkStage",
                    "MODELING",
                    "note",
                    "귀 형태 수정")),
            UUID.randomUUID().toString());
    org.assertj.core.api.Assertions.assertThat(row.get("productionStage").asText())
        .isEqualTo("MODELING_QUEUE");
    org.assertj.core.api.Assertions.assertThat(row.get("taskAttempt").asInt()).isEqualTo(2);
    row =
        send(
            modeler,
            "/api/production/tasks/" + row.get("taskId") + "/start",
            versionBody(row),
            UUID.randomUUID().toString());
    org.assertj.core.api.Assertions.assertThat(row.get("productionStage").asText())
        .isEqualTo("MODELING");
  }

  @Test
  void printRunSeparatesFailuresAndRetriesWithANewPlate() throws Exception {
    var first = readyForPlate();
    String designer = stranger;
    Long designerId = reviewerId;
    setup();
    var second = readyForPlate();
    second =
        send(
            owner,
            "/api/admin/orders/" + number + "/workflow/assign",
            jsonBody(Map.of("version", second.get("version").asLong(), "assigneeId", designerId)),
            UUID.randomUUID().toString());
    stranger = designer;
    reviewerId = designerId;
    var plate = confirmedPlate(List.of(first, second));
    String path = "/api/production/print-batches/" + plate.get("id");
    String body = confirmation(plate), key = UUID.randomUUID().toString();
    plate = send(printerToken, path + "/start", body, key);
    org.assertj.core.api.Assertions.assertThat(send(printerToken, path + "/start", body, key))
        .isEqualTo(plate);
    org.assertj.core.api.Assertions.assertThat(plate.get("status").asText()).isEqualTo("PRINTING");
    for (var row : plate.get("orders"))
      org.assertj.core.api.Assertions.assertThat(row.get("productionStage").asText())
          .isEqualTo("PRINTING");
    plate =
        send(
            printerToken,
            path + "/observations",
            jsonBody(
                Map.of(
                    "version",
                    plate.get("version").asLong(),
                    "note",
                    "중간 확인",
                    "purgeGrams",
                    12.5,
                    "issues",
                    List.of(
                        Map.of(
                            "orderNumber", second.get("orderNumber").asText(), "note", "서포트 들뜸")))),
            UUID.randomUUID().toString());
    org.assertj.core.api.Assertions.assertThat(plate.get("observations").size()).isEqualTo(1);
    body = finishBody(plate, second.get("orderNumber").asText());
    key = UUID.randomUUID().toString();
    plate = send(printerToken, path + "/finish", body, key);
    org.assertj.core.api.Assertions.assertThat(send(printerToken, path + "/finish", body, key))
        .isEqualTo(plate);
    org.assertj.core.api.Assertions.assertThat(plate.get("results").size()).isEqualTo(2);
    var successful =
        readJson(owner, "/api/admin/orders/" + first.get("orderNumber").asText() + "/workflow");
    var retry =
        readJson(stranger, "/api/admin/orders/" + second.get("orderNumber").asText() + "/workflow");
    org.assertj.core.api.Assertions.assertThat(successful.get("productionStage").asText())
        .isEqualTo("POST_PROCESSING");
    org.assertj.core.api.Assertions.assertThat(retry.get("productionStage").asText())
        .isEqualTo("PLATE_PREPARATION");
    org.assertj.core.api.Assertions.assertThat(retry.get("taskAttempt").asInt()).isEqualTo(2);
    org.assertj.core.api.Assertions.assertThat(retry.get("printBatch").isNull()).isTrue();
    var again = confirmedPlate(List.of(retry));
    String againPath = "/api/production/print-batches/" + again.get("id");
    again =
        send(printerToken, againPath + "/start", confirmation(again), UUID.randomUUID().toString());
    again =
        send(
            printerToken,
            againPath + "/finish",
            finishBody(again, null),
            UUID.randomUUID().toString());
    org.assertj.core.api.Assertions.assertThat(
            again.get("orders").get(0).get("productionStage").asText())
        .isEqualTo("POST_PROCESSING");
    org.assertj.core.api.Assertions.assertThat(
            again.get("orders").get(0).get("blockingIssues").toString())
        .doesNotContain("PRINT_FAILED");
  }

  @Test
  void finishingRequiresChecksAndQcWaitsForFulfillmentSettlement() throws Exception {
    var plate = printedPlate();
    var row = plate.get("orders").get(0);
    String taskPath = "/api/production/tasks/" + row.get("taskId");
    expectAs(printerToken, taskPath + "/post-processing", versionBody(row), 400);
    var config = readJson(owner, "/api/admin/production-compensation");
    config =
        send(
            owner,
            "/api/admin/production-compensation",
            jsonBody(
                Map.of(
                    "version",
                    config.get("version").asLong(),
                    "enabled",
                    false,
                    "paidWorkerIds",
                    List.of())),
            UUID.randomUUID().toString());
    send(
        owner,
        "/api/admin/production-compensation",
        jsonBody(
            Map.of(
                "version",
                config.get("version").asLong(),
                "enabled",
                true,
                "paidWorkerIds",
                List.of(plate.get("printingAssigneeId").asLong()))),
        UUID.randomUUID().toString());
    row =
        send(
            printerToken,
            taskPath + "/post-processing",
            postBody(row),
            UUID.randomUUID().toString());
    org.assertj.core.api.Assertions.assertThat(row.get("productionStage").asText()).isEqualTo("QC");
    taskPath = "/api/production/tasks/" + row.get("taskId") + "/quality-check";
    expectAs(
        printerToken,
        taskPath,
        jsonBody(
            Map.of(
                "version",
                row.get("version").asLong(),
                "decision",
                "PASSED",
                "checks",
                List.of("SURFACE"))),
        400);
    String body = qcBody(row), key = UUID.randomUUID().toString();
    row = send(printerToken, taskPath, body, key);
    org.assertj.core.api.Assertions.assertThat(send(printerToken, taskPath, body, key))
        .isEqualTo(row);
    org.assertj.core.api.Assertions.assertThat(row.get("productionStage").asText())
        .isEqualTo("PACKING");
    org.assertj.core.api.Assertions.assertThat(row.get("shipmentStatus").asText())
        .isEqualTo("NOT_READY");
    org.assertj.core.api.Assertions.assertThat(settlementCount(row.get("orderNumber").asText()))
        .isZero();
    mvc.perform(
            get("/api/admin/production-settlements")
                .header("Authorization", "Bearer " + printerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void qcFailureCreatesNewPostProcessingAttemptAndRetainsDecision() throws Exception {
    var row = printedPlate().get("orders").get(0);
    row =
        send(
            printerToken,
            "/api/production/tasks/" + row.get("taskId") + "/post-processing",
            postBody(row),
            UUID.randomUUID().toString());
    row =
        send(
            printerToken,
            "/api/production/tasks/" + row.get("taskId") + "/quality-check",
            jsonBody(
                Map.of(
                    "version",
                    row.get("version").asLong(),
                    "decision",
                    "FAILED",
                    "reasonCode",
                    "FINISH_DEFECT",
                    "reworkStage",
                    "POST_PROCESSING",
                    "note",
                    "눈 표면 재마감")),
            UUID.randomUUID().toString());
    org.assertj.core.api.Assertions.assertThat(row.get("productionStage").asText())
        .isEqualTo("POST_PROCESSING");
    org.assertj.core.api.Assertions.assertThat(row.get("taskAttempt").asInt()).isEqualTo(2);
    row =
        send(
            printerToken,
            "/api/production/tasks/" + row.get("taskId") + "/post-processing",
            postBody(row),
            UUID.randomUUID().toString());
    row =
        send(
            printerToken,
            "/api/production/tasks/" + row.get("taskId") + "/quality-check",
            qcBody(row),
            UUID.randomUUID().toString());
    org.assertj.core.api.Assertions.assertThat(row.get("productionStage").asText())
        .isEqualTo("PACKING");
    org.assertj.core.api.Assertions.assertThat(row.get("finishingHistory").size()).isEqualTo(4);
    org.assertj.core.api.Assertions.assertThat(row.get("blockingIssues").toString())
        .doesNotContain("QC_FAILED");
  }

  @Test
  void printingRejectsForeignWorkersAndStaleBatchWithoutStartingOrders() throws Exception {
    var plate = confirmedPlate(List.of(readyForPlate()));
    String path = "/api/production/print-batches/" + plate.get("id");
    expectAs(stranger, path + "/start", confirmation(plate), 403);
    var stale = new com.fasterxml.jackson.databind.ObjectMapper().readTree(confirmation(plate));
    ((com.fasterxml.jackson.databind.node.ObjectNode) stale).put("version", 0);
    expectAs(printerToken, path + "/start", stale.toString(), 409);
    var unchanged = readJson(printerToken, path);
    org.assertj.core.api.Assertions.assertThat(unchanged.get("status").asText())
        .isEqualTo("CONFIRMED");
    plate = send(printerToken, path + "/start", confirmation(plate), UUID.randomUUID().toString());
    expectAs(
        printerToken,
        path + "/finish",
        jsonBody(Map.of("version", plate.get("version").asLong(), "orders", List.of())),
        400);
  }

  com.fasterxml.jackson.databind.JsonNode confirmedPlate(
      List<com.fasterxml.jackson.databind.JsonNode> rows) throws Exception {
    var plate = uploadPlate(createPlate(rows));
    return send(
        stranger,
        "/api/production/print-batches/" + plate.get("id") + "/confirm",
        confirmation(plate),
        UUID.randomUUID().toString());
  }

  com.fasterxml.jackson.databind.JsonNode printedPlate() throws Exception {
    var plate = confirmedPlate(List.of(readyForPlate()));
    String path = "/api/production/print-batches/" + plate.get("id");
    plate = send(printerToken, path + "/start", confirmation(plate), UUID.randomUUID().toString());
    return send(
        printerToken, path + "/finish", finishBody(plate, null), UUID.randomUUID().toString());
  }

  String finishBody(com.fasterxml.jackson.databind.JsonNode plate, String failed) throws Exception {
    var rows = new ArrayList<Map<String, Object>>();
    for (var row : plate.get("orders"))
      rows.add(
          Map.of(
              "orderNumber",
              row.get("orderNumber").asText(),
              "version",
              row.get("version").asLong(),
              "result",
              row.get("orderNumber").asText().equals(failed) ? "FAILED" : "SUCCESS",
              "note",
              row.get("orderNumber").asText().equals(failed) ? "서포트 들뜸" : ""));
    return jsonBody(Map.of("version", plate.get("version").asLong(), "orders", rows));
  }

  String postBody(com.fasterxml.jackson.databind.JsonNode row) throws Exception {
    return jsonBody(
        Map.of(
            "version",
            row.get("version").asLong(),
            "checks",
            List.of("SUPPORT_REMOVED", "SURFACE_CHECKED"),
            "resinCuring",
            "NOT_APPLICABLE"));
  }

  String qcBody(com.fasterxml.jackson.databind.JsonNode row) throws Exception {
    return jsonBody(
        Map.of(
            "version",
            row.get("version").asLong(),
            "decision",
            "PASSED",
            "checks",
            List.of("SHAPE_COLOR", "SURFACE", "EYES_NOSE")));
  }

  void expectAs(String token, String path, String body, int code) throws Exception {
    mvc.perform(
            post(path)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(body))
        .andExpect(status().is(code));
  }

  @Test
  void plateConfirmsTwoOrdersWithOneFileAndHandsOffExactlyOnce() throws Exception {
    var first = readyForPlate();
    String designerToken = stranger;
    Long designer = reviewerId;
    setup();
    var second = readyForPlate();
    second =
        send(
            owner,
            "/api/admin/orders/" + number + "/workflow/assign",
            jsonBody(Map.of("version", second.get("version").asLong(), "assigneeId", designer)),
            UUID.randomUUID().toString());
    stranger = designerToken;
    reviewerId = designer;
    var plate = createPlate(List.of(first, second));
    String path = "/api/production/print-batches/" + plate.get("id");
    expectPlateStatus(path + "/confirm", confirmation(plate), 422);
    plate = uploadPlate(plate);
    String body = confirmation(plate), key = UUID.randomUUID().toString();
    var done = send(stranger, path + "/confirm", body, key);
    org.assertj.core.api.Assertions.assertThat(send(stranger, path + "/confirm", body, key))
        .isEqualTo(done);
    org.assertj.core.api.Assertions.assertThat(done.get("status").asText()).isEqualTo("CONFIRMED");
    for (var row : done.get("orders")) {
      org.assertj.core.api.Assertions.assertThat(row.get("productionStage").asText())
          .isEqualTo("PRINT_QUEUE");
      org.assertj.core.api.Assertions.assertThat(row.get("assignee").get("id").asLong())
          .isEqualTo(plate.get("printingAssigneeId").asLong());
      org.assertj.core.api.Assertions.assertThat(
              tasks.findByOrderNumberOrderByIdAsc(row.get("orderNumber").asText()).stream()
                  .filter(t -> t.getStage() == ProductionStage.PRINT_QUEUE)
                  .count())
          .isEqualTo(1);
    }
    mvc.perform(get(path).header("Authorization", "Bearer " + printerToken))
        .andExpect(status().isOk());
    org.mockito.Mockito.when(
            storage.presignDownload(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new GoodsSurveyPhotoStorage.PresignedDownload(
                "https://storage.example.test/plate.3mf", Instant.now().plusSeconds(300)));
    mvc.perform(
            post(path
                    + "/artifacts/"
                    + done.get("artifacts").get(0).get("id").asText()
                    + "/download-link")
                .header("Authorization", "Bearer " + printerToken))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void plateLayoutChangeInvalidatesFileAndCancellationReleasesOrder() throws Exception {
    var plate = uploadPlate(createPlate(List.of(readyForPlate())));
    String path = "/api/production/print-batches/" + plate.get("id");
    var body = plateConfig(plate.get("orders"), plate.get("printingAssigneeId").asLong());
    body.put("version", plate.get("version").asLong());
    body.put("printerName", "다른 프린터");
    plate = send(stranger, path, jsonBody(body), UUID.randomUUID().toString());
    expectPlateStatus(path + "/confirm", confirmation(plate), 422);
    org.assertj.core.api.Assertions.assertThat(
            plate.get("artifacts").get(0).get("currentLayout").asBoolean())
        .isFalse();
    var row = plate.get("orders").get(0);
    mvc.perform(
            post("/api/admin/orders/" + number + "/workflow/assign")
                .header("Authorization", "Bearer " + owner)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(
                    jsonBody(
                        Map.of("version", row.get("version").asLong(), "assigneeId", reviewerId))))
        .andExpect(status().isBadRequest());
    send(stranger, path + "/cancel", versionBody(plate), UUID.randomUUID().toString());
    var available = readJson(stranger, "/api/production/print-batches/candidates");
    var restored =
        java.util.stream.StreamSupport.stream(available.spliterator(), false)
            .filter(r -> r.get("orderNumber").asText().equals(number))
            .findFirst()
            .orElseThrow();
    createPlate(List.of(restored));
  }

  @Test
  void plateRejectsMissingSlotsRevokedPermissionAndChangedOrderAtomically() throws Exception {
    var plate = uploadPlate(createPlate(List.of(readyForPlate())));
    String path = "/api/production/print-batches/" + plate.get("id");
    var body = plateConfig(plate.get("orders"), plate.get("printingAssigneeId").asLong());
    body.put("version", plate.get("version").asLong());
    body.put("slots", List.of());
    var draft = send(stranger, path, jsonBody(body), UUID.randomUUID().toString());
    expectPlateStatus(path + "/confirm", confirmation(draft), 400);
    overrides.saveAndFlush(
        StaffPermissionOverride.of(reviewerId, PermissionKey.VIEW_FILAMENT, false, null, "권한 회수"));
    mvc.perform(get(path).header("Authorization", "Bearer " + stranger))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(path + "/confirm")
                .header("Authorization", "Bearer " + stranger)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(confirmation(draft)))
        .andExpect(status().isForbidden());
    // Administrative cancellation must recover reservations even when the creator lost permission.
    send(owner, path + "/cancel", versionBody(draft), UUID.randomUUID().toString());
    org.assertj.core.api.Assertions.assertThat(
            orders.findByOrderNumber(number).orElseThrow().getProductionStage())
        .isEqualTo(ProductionStage.PLATE_PREPARATION);
  }

  @Test
  void plateRejectsCanceledMemberWithoutAdvancingOtherOrders() throws Exception {
    var first = readyForPlate();
    String original = stranger;
    Long designer = reviewerId;
    setup();
    var second = readyForPlate();
    second =
        send(
            owner,
            "/api/admin/orders/" + number + "/workflow/assign",
            jsonBody(Map.of("version", second.get("version").asLong(), "assigneeId", designer)),
            UUID.randomUUID().toString());
    stranger = original;
    reviewerId = designer;
    var plate = uploadPlate(createPlate(List.of(first, second)));
    var canceled = orders.findByOrderNumber(number).orElseThrow();
    canceled.cancel(GoodsOrderStatus.CANCELED, "테스트 취소");
    orders.saveAndFlush(canceled);
    expectPlateStatus(
        "/api/production/print-batches/" + plate.get("id") + "/confirm", confirmation(plate), 409);
    org.assertj.core.api.Assertions.assertThat(
            orders
                .findByOrderNumber(first.get("orderNumber").asText())
                .orElseThrow()
                .getProductionStage())
        .isEqualTo(ProductionStage.PLATE_PREPARATION);
  }

  @Test
  void plateReservationIsExclusiveAndForeignWorkersCannotRead() throws Exception {
    var row = readyForPlate();
    var plate = createPlate(List.of(row));
    expectPlateStatus(
        "/api/production/print-batches",
        jsonBody(plateConfig(List.of(row), plate.get("printingAssigneeId").asLong())),
        409);
    mvc.perform(get("/api/production/print-batches").header("Authorization", "Bearer " + modeler))
        .andExpect(status().isForbidden());
    String other = account(AdminRole.PRODUCTION, Set.of(WorkRole.DESIGN_QC));
    mvc.perform(
            get("/api/production/print-batches/" + plate.get("id"))
                .header("Authorization", "Bearer " + other))
        .andExpect(status().isNotFound());
    expectPlateStatus(
        "/api/production/print-batches/" + plate.get("id") + "/artifacts/upload-requests",
        jsonBody(
            Map.of(
                "version", plate.get("version").asLong(), "fileName", "../plate.exe", "size", 10)),
        400);
  }

  String printerToken;

  @Test
  void plateConcurrentConfirmCreatesOnlyOneTaskPerOrder() throws Exception {
    var plate = uploadPlate(createPlate(List.of(readyForPlate())));
    String path = "/api/production/print-batches/" + plate.get("id") + "/confirm",
        body = confirmation(plate);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var gate = new java.util.concurrent.CountDownLatch(1);
      var results = new ArrayList<java.util.concurrent.Future<Integer>>();
      for (int i = 0; i < 2; i++)
        results.add(
            pool.submit(
                () -> {
                  gate.await();
                  return mvc.perform(
                          post(path)
                              .header("Authorization", "Bearer " + stranger)
                              .header("Idempotency-Key", UUID.randomUUID().toString())
                              .contentType("application/json")
                              .content(body))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      gate.countDown();
      var statuses = new ArrayList<Integer>();
      for (var result : results)
        statuses.add(result.get(30, java.util.concurrent.TimeUnit.SECONDS));
      org.assertj.core.api.Assertions.assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    } finally {
      pool.shutdownNow();
    }
    org.assertj.core.api.Assertions.assertThat(
            tasks.findByOrderNumberOrderByIdAsc(number).stream()
                .filter(t -> t.getStage() == ProductionStage.PRINT_QUEUE)
                .count())
        .isEqualTo(1);
  }

  @Test
  void plateRejectsWrongFileMetadataAndTransfersWholeBatchToReplacementWorker() throws Exception {
    var plate = createPlate(List.of(readyForPlate()));
    String path = "/api/production/print-batches/" + plate.get("id");
    org.mockito.Mockito.when(
            storage.presignUpload(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new GoodsSurveyPhotoStorage.PresignedUpload(
                "https://storage.example.test/put", Map.of(), Instant.now().plusSeconds(600)));
    var request =
        send(
            stranger,
            path + "/artifacts/upload-requests",
            jsonBody(
                Map.of(
                    "version", plate.get("version").asLong(), "fileName", "plate.3mf", "size", 10)),
            UUID.randomUUID().toString());
    org.mockito.Mockito.when(storage.head(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(
            new GoodsSurveyPhotoStorage.StoredObject(
                9, "application/octet-stream", new byte[] {80, 75, 3, 4}));
    expectPlateStatus(
        path + "/artifacts/" + request.get("artifactId").asText() + "/confirm",
        versionBody(request),
        400);
    plate = readJson(stranger, path);
    plate = uploadPlate(plate);
    plate = send(stranger, path + "/confirm", confirmation(plate), UUID.randomUUID().toString());
    String replacement = account(AdminRole.PRODUCTION, Set.of(WorkRole.PRINT_FINISHING));
    Long replacementId = readJson(replacement, "/api/admin/me").get("id").asLong();
    var input = plateConfig(plate.get("orders"), replacementId);
    input.put("version", plate.get("version").asLong());
    plate = send(owner, path + "/assign", jsonBody(input), UUID.randomUUID().toString());
    org.assertj.core.api.Assertions.assertThat(
            plate.get("orders").get(0).get("assignee").get("id").asLong())
        .isEqualTo(replacementId);
    readJson(replacement, path);
    mvc.perform(get(path).header("Authorization", "Bearer " + printerToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void printingDefaultIsOptionalAndOlderSettingsClientsPreserveIt() throws Exception {
    String worker = account(AdminRole.PRODUCTION, Set.of(WorkRole.PRINT_FINISHING));
    Long workerId = readJson(worker, "/api/admin/me").get("id").asLong();
    var defaults = readJson(owner, "/api/admin/workflow/default-assignees");
    var payload = new LinkedHashMap<String, Object>();
    payload.put("version", defaults.get("version").asLong());
    payload.put("modeling", modelerId);
    payload.put("review", reviewerId);
    payload.put("printing", workerId);
    mvc.perform(
            patch("/api/admin/workflow/default-assignees")
                .header("Authorization", "Bearer " + owner)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(jsonBody(payload)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.printing").value(workerId));
    defaults = readJson(owner, "/api/admin/workflow/default-assignees");
    payload.remove("printing");
    payload.put("version", defaults.get("version").asLong());
    mvc.perform(
            patch("/api/admin/workflow/default-assignees")
                .header("Authorization", "Bearer " + owner)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(jsonBody(payload)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.printing").value(workerId));
  }

  com.fasterxml.jackson.databind.JsonNode readyForPlate() throws Exception {
    var row = readyForMapping();
    var f = createFilament();
    return send(
        stranger,
        "/api/production/tasks/" + row.get("taskId") + "/filament-mappings",
        mappingBody(row, f, true),
        UUID.randomUUID().toString());
  }

  String jsonBody(Object value) throws Exception {
    return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
  }

  com.fasterxml.jackson.databind.JsonNode readJson(String token, String path) throws Exception {
    var result =
        mvc.perform(get(path).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    return new com.fasterxml.jackson.databind.ObjectMapper()
        .readTree(result.getResponse().getContentAsString())
        .get("data");
  }

  Map<String, Object> plateConfig(
      Iterable<com.fasterxml.jackson.databind.JsonNode> rows, Long printer) {
    var selected = new ArrayList<Map<String, Object>>();
    var spools = new LinkedHashSet<Long>();
    for (var row : rows) {
      selected.add(
          Map.of(
              "orderNumber",
              row.get("orderNumber").asText(),
              "version",
              row.get("version").asLong()));
      for (var mapping : row.get("filamentMappings"))
        spools.add(mapping.get("filamentId").asLong());
    }
    var slots = new ArrayList<Map<String, Object>>();
    for (Long spool : spools)
      slots.add(Map.of("slotLabel", "AMS-" + (slots.size() + 1), "filamentId", spool));
    var body = new LinkedHashMap<String, Object>();
    body.put("orders", selected);
    body.put("printerName", "테스트 프린터");
    body.put("slots", slots);
    body.put("printingAssigneeId", printer);
    return body;
  }

  com.fasterxml.jackson.databind.JsonNode createPlate(
      List<com.fasterxml.jackson.databind.JsonNode> rows) throws Exception {
    printerToken = account(AdminRole.PRODUCTION, Set.of(WorkRole.PRINT_FINISHING));
    Long printer = readJson(printerToken, "/api/admin/me").get("id").asLong();
    return send(
        stranger,
        "/api/production/print-batches",
        jsonBody(plateConfig(rows, printer)),
        UUID.randomUUID().toString());
  }

  String confirmation(com.fasterxml.jackson.databind.JsonNode plate) throws Exception {
    var body = plateConfig(plate.get("orders"), plate.get("printingAssigneeId").asLong());
    body.put("version", plate.get("version").asLong());
    body.put("layoutChecked", true);
    return jsonBody(body);
  }

  com.fasterxml.jackson.databind.JsonNode uploadPlate(com.fasterxml.jackson.databind.JsonNode plate)
      throws Exception {
    org.mockito.Mockito.when(
            storage.presignUpload(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new GoodsSurveyPhotoStorage.PresignedUpload(
                "https://storage.example.test/put", Map.of(), Instant.now().plusSeconds(600)));
    String path = "/api/production/print-batches/" + plate.get("id");
    var pending =
        send(
            stranger,
            path + "/artifacts/upload-requests",
            jsonBody(
                Map.of(
                    "version", plate.get("version").asLong(), "fileName", "plate.3mf", "size", 10)),
            UUID.randomUUID().toString());
    org.mockito.Mockito.when(storage.head(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(
            new GoodsSurveyPhotoStorage.StoredObject(
                10, "application/octet-stream", new byte[] {80, 75, 3, 4, 0, 0, 0, 0}));
    return send(
        stranger,
        path + "/artifacts/" + pending.get("artifactId").asText() + "/confirm",
        versionBody(pending),
        UUID.randomUUID().toString());
  }

  void expectPlateStatus(String path, String body, int status) throws Exception {
    mvc.perform(
            post(path)
                .header("Authorization", "Bearer " + stranger)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(body))
        .andExpect(status().is(status));
  }

  @Test
  void filamentMappingRetainsAllPartsAndHonorsPermissionRevocation() throws Exception {
    var row = readyForMapping();
    var f = createFilament();
    String path = "/api/production/tasks/" + row.get("taskId") + "/filament-mappings";
    var entries = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < 64; i++)
      entries.add(Map.of("partName", "부위" + i, "filamentId", f.get("id").asLong()));
    String body =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsString(
                Map.of(
                    "version",
                    row.get("version").asLong(),
                    "complete",
                    false,
                    "mappings",
                    entries));
    var saved = send(stranger, path, body, UUID.randomUUID().toString());
    org.assertj.core.api.Assertions.assertThat(saved.get("filamentMappings").size()).isEqualTo(64);
    overrides.saveAndFlush(
        StaffPermissionOverride.of(
            reviewerId, PermissionKey.VIEW_FILAMENT, false, null, "조회 차단 테스트"));
    mvc.perform(
            get("/api/admin/orders/" + number + "/workflow")
                .header("Authorization", "Bearer " + stranger))
        .andExpect(jsonPath("$.data.filamentMappings").isEmpty())
        .andExpect(jsonPath("$.data.allowedActions").isEmpty());
    mvc.perform(
            post(path)
                .header("Authorization", "Bearer " + stranger)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(mappingBody(saved, f, true)))
        .andExpect(status().isForbidden());
  }

  @Test
  void filamentCatalogRequiresPermissionAndPreservesSpoolIdentity() throws Exception {
    String body = filamentBody("SP-" + UUID.randomUUID(), "크림", 0, true);
    String key = UUID.randomUUID().toString();
    var f = send(owner, "/api/admin/filaments", body, key);
    org.assertj.core.api.Assertions.assertThat(send(owner, "/api/admin/filaments", body, key))
        .isEqualTo(f);
    for (String token : List.of(modeler, stranger))
      mvc.perform(
              post("/api/admin/filaments")
                  .header("Authorization", "Bearer " + token)
                  .header("Idempotency-Key", UUID.randomUUID().toString())
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/filaments").header("Authorization", "Bearer " + stranger))
        .andExpect(status().isOk());
    mvc.perform(get("/api/admin/filaments").header("Authorization", "Bearer " + modeler))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/admin/filaments")
                .header("Authorization", "Bearer " + owner)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isConflict());
    String path = "/api/admin/filaments/" + f.get("id").asLong();
    send(
        owner,
        path,
        filamentBody(f.get("spoolId").asText(), "흰색", f.get("version").asLong(), false),
        UUID.randomUUID().toString());
    mvc.perform(
            post(path)
                .header("Authorization", "Bearer " + owner)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isConflict());
  }

  @Test
  void filamentMappingSavesResumesAndCompletesExactlyOnceWithSnapshot() throws Exception {
    var row = readyForMapping();
    var f = createFilament();
    String path = "/api/production/tasks/" + row.get("taskId") + "/filament-mappings";
    row = send(stranger, path, mappingBody(row, f, false), UUID.randomUUID().toString());
    org.assertj.core.api.Assertions.assertThat(row.get("productionStage").asText())
        .isEqualTo("COLOR_MAPPING");
    mvc.perform(
            get("/api/admin/orders/" + number + "/workflow")
                .header("Authorization", "Bearer " + stranger))
        .andExpect(jsonPath("$.data.filamentMappings[0].partName").value("몸통"))
        .andExpect(jsonPath("$.data.filamentMappings[0].spoolId").value(f.get("spoolId").asText()));
    String body = mappingBody(row, f, true), key = UUID.randomUUID().toString();
    var done = send(stranger, path, body, key);
    org.assertj.core.api.Assertions.assertThat(send(stranger, path, body, key)).isEqualTo(done);
    org.assertj.core.api.Assertions.assertThat(done.get("productionStage").asText())
        .isEqualTo("PLATE_PREPARATION");
    org.assertj.core.api.Assertions.assertThat(done.get("assignee").get("id").asLong())
        .isEqualTo(reviewerId);
    org.assertj.core.api.Assertions.assertThat(
            tasks.findByOrderNumberOrderByIdAsc(number).stream()
                .filter(t -> t.getStage() == ProductionStage.PLATE_PREPARATION)
                .count())
        .isEqualTo(1);
    send(
        owner,
        "/api/admin/filaments/" + f.get("id"),
        filamentBody(f.get("spoolId").asText(), "색상명 수정", f.get("version").asLong(), false),
        UUID.randomUUID().toString());
    mvc.perform(
            get("/api/admin/orders/" + number + "/workflow")
                .header("Authorization", "Bearer " + stranger))
        .andExpect(jsonPath("$.data.filamentMappings[0].colorName").value("크림"))
        .andExpect(jsonPath("$.data.filamentMappings[0].completedAt").isNotEmpty());
    mvc.perform(
            post(path)
                .header("Authorization", "Bearer " + stranger)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(mappingBody(done, f, false)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void filamentMappingRejectsColorOnlyDuplicatesInactiveSpoolsAndWrongActor() throws Exception {
    var row = readyForMapping();
    var f = createFilament();
    String path = "/api/production/tasks/" + row.get("taskId") + "/filament-mappings";
    // 색상 작업은 공동 작업함에 있다. 대표는 색상 역할이 없고 모델러는
    // 역할이 달라 막히지만, 색상 역할의 다른 실무자는 이어받을 수 있다.
    for (String token : List.of(owner, modeler))
      mvc.perform(
              post(path)
                  .header("Authorization", "Bearer " + token)
                  .header("Idempotency-Key", UUID.randomUUID().toString())
                  .contentType("application/json")
                  .content(mappingBody(row, f, true)))
          .andExpect(status().isForbidden());
    for (String entries :
        List.of(
            "[]",
            "[{\"partName\":\"몸통\",\"colorName\":\"크림\"}]",
            "[{\"partName\":\"몸통\",\"filamentId\":"
                + f.get("id")
                + "},{\"partName\":\" 몸통 \",\"filamentId\":"
                + f.get("id")
                + "}]"))
      mvc.perform(
              post(path)
                  .header("Authorization", "Bearer " + stranger)
                  .header("Idempotency-Key", UUID.randomUUID().toString())
                  .contentType("application/json")
                  .content(
                      "{\"version\":"
                          + row.get("version")
                          + ",\"complete\":true,\"mappings\":"
                          + entries
                          + "}"))
          .andExpect(status().isBadRequest());
    send(
        owner,
        "/api/admin/filaments/" + f.get("id"),
        filamentBody(f.get("spoolId").asText(), "크림", f.get("version").asLong(), false),
        UUID.randomUUID().toString());
    mvc.perform(
            post(path)
                .header("Authorization", "Bearer " + stranger)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(mappingBody(row, f, true)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void concurrentFilamentCompletionCreatesOnlyOnePlateTask() throws Exception {
    var row = readyForMapping();
    var f = createFilament();
    String path = "/api/production/tasks/" + row.get("taskId") + "/filament-mappings";
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var gate = new java.util.concurrent.CountDownLatch(1);
      var results = new ArrayList<java.util.concurrent.Future<Integer>>();
      for (int i = 0; i < 2; i++)
        results.add(
            pool.submit(
                () -> {
                  gate.await();
                  return mvc.perform(
                          post(path)
                              .header("Authorization", "Bearer " + stranger)
                              .header("Idempotency-Key", UUID.randomUUID().toString())
                              .contentType("application/json")
                              .content(mappingBody(row, f, true)))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      gate.countDown();
      var statuses = new ArrayList<Integer>();
      for (var result : results)
        statuses.add(result.get(30, java.util.concurrent.TimeUnit.SECONDS));
      org.assertj.core.api.Assertions.assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    } finally {
      pool.shutdownNow();
    }
    org.assertj.core.api.Assertions.assertThat(
            tasks.findByOrderNumberOrderByIdAsc(number).stream()
                .filter(t -> t.getStage() == ProductionStage.PLATE_PREPARATION)
                .count())
        .isEqualTo(1);
  }

  com.fasterxml.jackson.databind.JsonNode readyForMapping() throws Exception {
    var row = readyForReview();
    return send(
        stranger,
        reviewPath(row),
        reviewBody(row, "APPROVED", null, ""),
        UUID.randomUUID().toString());
  }

  com.fasterxml.jackson.databind.JsonNode createFilament() throws Exception {
    return send(
        owner,
        "/api/admin/filaments",
        filamentBody("SP-" + UUID.randomUUID(), "크림", 0, true),
        UUID.randomUUID().toString());
  }

  String filamentBody(String spool, String color, long version, boolean active) {
    return "{\"spoolId\":\""
        + spool
        + "\",\"colorName\":\""
        + color
        + "\",\"material\":\"PLA\",\"finish\":\"무광\",\"manufacturer\":\"Test\",\"source\":\"테스트"
        + " 구매처\",\"priceKrw\":20000,\"remainingGrams\":800,\"active\":"
        + active
        + ",\"version\":"
        + version
        + "}";
  }

  String mappingBody(
      com.fasterxml.jackson.databind.JsonNode row,
      com.fasterxml.jackson.databind.JsonNode f,
      boolean complete) {
    return "{\"version\":"
        + row.get("version")
        + ",\"complete\":"
        + complete
        + ",\"mappings\":[{\"partName\":\"몸통\",\"filamentId\":"
        + f.get("id")
        + "}]}";
  }

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
    a.acceptInvite("unused-test-hash");
    a.approve(roles, Instant.now());
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
  void anotherDesignQcTakesOverAWaitingReview() throws Exception {
    // 공동 작업함. 배정된 사람이 자리를 비워도 검수 역할의 다른 실무자가
    // 이어받는다. 대표가 매번 다시 배정해야 하면 사람 하나가 빠질 때마다
    // 제작이 멈춘다.
    var row = readyForReview();
    String other = account(AdminRole.PRODUCTION, Set.of(WorkRole.DESIGN_QC));

    var result =
        send(
            other,
            reviewPath(row),
            reviewBody(row, "APPROVED", null, "다른 검수자가 이어받음"),
            UUID.randomUUID().toString());

    org.assertj.core.api.Assertions.assertThat(result.get("productionStage").asText())
        .isEqualTo("COLOR_MAPPING");
  }

  @Test
  void reviewRequiresAssignedReviewerCurrentPermissionAndValidReason() throws Exception {
    var row = readyForReview();
    // 검수도 공동 작업함이다. 검수 역할의 다른 실무자는 이어받을 수 있고,
    // 역할이 없는 대표나 역할이 다른 모델러는 막힌다.
    for (String unauthorized : List.of(owner, modeler)) {
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
