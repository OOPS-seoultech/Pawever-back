package com.pawever.backend.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(
    named = "WORKFLOW_JDBC_URL",
    matches = "jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/workflow_validation[a-zA-Z0-9_]*")
class WorkflowMigrationTest {
  @Test
  void v17AndV18PreserveHistoricalOrdersAndExistingReviewTasks() throws Exception {
    String base = System.getenv("WORKFLOW_JDBC_URL"),
        user = System.getenv("WORKFLOW_DB_USER"),
        password = System.getenv("WORKFLOW_DB_PASSWORD");
    String schema =
        "workflow_validation_migration_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = DriverManager.getConnection(base, user, password);
        var s = connection.createStatement()) {
      s.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
    }
    String url = base.substring(0, base.lastIndexOf('/') + 1) + schema;
    Flyway.configure().dataSource(url, user, password).target("16").load().migrate();
    List<String> statuses =
        List.of(
            "PAYMENT_PENDING",
            "PAYMENT_COMPLETED",
            "IN_PRODUCTION",
            "SHIPPED",
            "PICKED_UP",
            "CANCELED",
            "CANCEL_FAILED",
            "PAYMENT_EXPIRED",
            "PAYMENT_FAILED",
            "LEGACY_FREE");
    try (var c = DriverManager.getConnection(url, user, password);
        var s = c.createStatement()) {
      s.execute(
          "INSERT INTO"
              + " goods_survey_campaigns(id,capacity,historical_allocated,starts_at,ends_at,survey_open,goods_open,channel)"
              + " VALUES('migration-fixture',100,0,NOW(),DATE_ADD(NOW(),INTERVAL 7"
              + " DAY),1,1,'ONLINE')");
      for (int i = 0; i < statuses.size(); i++) {
        String id = UUID.randomUUID().toString(), status = statuses.get(i);
        s.execute(
            "INSERT INTO"
                + " goods_survey_responses(id,campaign_id,questionnaire_version,edit_token_hash,status,selected_goods)"
                + " VALUES('"
                + id
                + "','migration-fixture','v1','"
                + id
                + "','SUBMITTED','figure')");
        s.execute(
            "INSERT INTO"
                + " goods_survey_fulfillments(response_id,idempotency_key,conversion_event_id,tracking_json,goods_type,pet_name,guardian_name,phone,phone_hash,privacy_consent_version,privacy_consented_at,order_number,status,delivery_method,payment_amount_krw,payment_expires_at,paid_at,keyring_added)"
                + " VALUES('"
                + id
                + "','"
                + id
                + "','"
                + id
                + "','{}','figure','test','test','test','"
                + id
                + "','v1',NOW(),'PE-MIG-"
                + i
                + "','"
                + status
                + "','PICKUP',18900,'2026-09-19 12:00:00',"
                + (Set.of(
                            "PAYMENT_COMPLETED",
                            "IN_PRODUCTION",
                            "SHIPPED",
                            "PICKED_UP",
                            "CANCELED",
                            "CANCEL_FAILED")
                        .contains(status)
                    ? "'2026-09-12 12:00:00'"
                    : "NULL")
                + ",1)");
      }
    }
    var flyway = Flyway.configure().dataSource(url, user, password).target("17").load();
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(flyway.migrate().migrationsExecuted).isZero();
    try (var c = DriverManager.getConnection(url, user, password);
        var s = c.createStatement()) {
      try (var rows =
          s.executeQuery(
              "SELECT"
                  + " order_number,status,production_stage,payment_amount_krw,payment_expires_at,lifecycle_payment_status,lifecycle_shipment_status,keyring_added"
                  + " FROM goods_survey_fulfillments ORDER BY id")) {
        int i = 0;
        while (rows.next()) {
          assertThat(rows.getString("status")).isEqualTo(statuses.get(i++));
          assertThat(rows.getString("production_stage")).isNull();
          assertThat(rows.getInt("payment_amount_krw")).isEqualTo(18900);
          assertThat(rows.getString("payment_expires_at")).startsWith("2026-09-19 12:00:00");
          assertThat(rows.getBoolean("keyring_added")).isTrue();
          if (rows.getString("status").equals("LEGACY_FREE"))
            assertThat(rows.getString("lifecycle_payment_status")).isEqualTo("NOT_REQUIRED");
          if (rows.getString("status").equals("SHIPPED"))
            assertThat(rows.getString("lifecycle_shipment_status")).isEqualTo("ACCEPTED");
        }
        assertThat(i).isEqualTo(statuses.size());
      }
      try (var rows =
          s.executeQuery(
              "SELECT (SELECT COUNT(*) FROM production_tasks)+(SELECT COUNT(*) FROM admin_accounts"
                  + " WHERE role='OWNER')")) {
        rows.next();
        assertThat(rows.getInt(1)).isZero();
      }
      s.execute(
          "INSERT INTO production_tasks(id,order_number,stage,attempt,assignee_id,status) VALUES"
              + "(7001,'PE-MIG-2','MODELING',1,11,'COMPLETED'),(7002,'PE-MIG-2','MODEL_REVIEW',1,12,'WAITING')");
      s.execute(
          "INSERT INTO"
              + " production_artifacts(id,order_number,task_id,uploader_id,kind,file_name,content_type,object_key,expected_size,confirmed,expires_at)"
              + " VALUES('existing-model','PE-MIG-2',7001,11,'MODEL_SOURCE','model.blend','application/octet-stream','existing/model.blend',1234,1,NOW())");
    }
    var latest = Flyway.configure().dataSource(url, user, password).target("18").load();
    assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(latest.migrate().migrationsExecuted).isZero();
    try (var c = DriverManager.getConnection(url, user, password);
        var s = c.createStatement()) {
      try (var rows =
          s.executeQuery(
              "SELECT stage,attempt,assignee_id,status FROM production_tasks ORDER BY id")) {
        rows.next();
        assertThat(rows.getString("stage")).isEqualTo("MODELING");
        assertThat(rows.getString("status")).isEqualTo("COMPLETED");
        rows.next();
        assertThat(rows.getString("stage")).isEqualTo("MODEL_REVIEW");
        assertThat(rows.getInt("attempt")).isEqualTo(1);
        assertThat(rows.getLong("assignee_id")).isEqualTo(12);
        assertThat(rows.getString("status")).isEqualTo("WAITING");
        assertThat(rows.next()).isFalse();
      }
      try (var rows =
          s.executeQuery(
              "SELECT (SELECT COUNT(*) FROM model_reviews),task_id,expected_size,confirmed FROM"
                  + " production_artifacts WHERE id='existing-model'")) {
        rows.next();
        assertThat(rows.getLong(1)).isZero();
        assertThat(rows.getLong(2)).isEqualTo(7001);
        assertThat(rows.getLong(3)).isEqualTo(1234);
        assertThat(rows.getBoolean(4)).isTrue();
      }
      s.execute(
          "INSERT INTO"
              + " model_reviews(order_number,review_task_id,modeling_task_id,modeling_attempt,reviewer_id,decision,note,approved_checks,reviewed_at)"
              + " VALUES"
              + " ('PE-MIG-2',7002,7001,1,12,'APPROVED','','BASE_CUT,FEATURES,LIKENESS,PRINTABILITY',NOW())");
    }
    var filamentVersion = Flyway.configure().dataSource(url, user, password).target("19").load();
    assertThat(filamentVersion.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(filamentVersion.migrate().migrationsExecuted).isZero();
    try (var c = DriverManager.getConnection(url, user, password);
        var s = c.createStatement();
        var rows =
            s.executeQuery(
                "SELECT (SELECT COUNT(*) FROM filaments),(SELECT COUNT(*) FROM"
                    + " order_filament_mappings),(SELECT COUNT(*) FROM model_reviews WHERE"
                    + " review_task_id=7002 AND decision='APPROVED'),(SELECT COUNT(*) FROM"
                    + " production_tasks),(SELECT COUNT(*) FROM goods_survey_fulfillments)")) {
      rows.next();
      assertThat(rows.getLong(1)).isZero();
      assertThat(rows.getLong(2)).isZero();
      assertThat(rows.getLong(3)).isEqualTo(1);
      assertThat(rows.getLong(4)).isEqualTo(2);
      assertThat(rows.getLong(5)).isEqualTo(10);
    }
    try (var c = DriverManager.getConnection(url, user, password);
        var s = c.createStatement()) {
      s.execute(
          "INSERT INTO"
              + " filaments(id,version,spool_id,color_name,material,finish,manufacturer,source,remaining_grams,active,updated_at)"
              + " VALUES(901,0,'MIG-SPOOL','cream','PLA','matte','','',800,1,NOW())");
      s.execute(
          "INSERT INTO"
              + " order_filament_mappings(order_number,task_id,modeling_attempt,part_name,part_key,filament_id,spool_id,color_name,material,finish,saved_by,saved_at,completed_at)"
              + " VALUES('PE-MIG-2',7003,1,'body','body',901,'MIG-SPOOL','cream','PLA','matte',12,NOW(),NOW())");
    }
    var plateVersion = Flyway.configure().dataSource(url, user, password).target("20").load();
    assertThat(plateVersion.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(plateVersion.migrate().migrationsExecuted).isZero();
    try (var c = DriverManager.getConnection(url, user, password);
        var s = c.createStatement();
        var rows =
            s.executeQuery(
                "SELECT (SELECT COUNT(*) FROM print_batches),(SELECT COUNT(*) FROM"
                    + " print_batch_items),(SELECT COUNT(*) FROM print_batch_artifacts),(SELECT"
                    + " COUNT(*) FROM filaments WHERE id=901 AND remaining_grams=800),(SELECT"
                    + " COUNT(*) FROM order_filament_mappings WHERE filament_id=901 AND"
                    + " completed_at IS NOT NULL),(SELECT COUNT(*) FROM model_reviews),(SELECT"
                    + " COUNT(*) FROM production_tasks),(SELECT COUNT(*) FROM workflow_settings"
                    + " WHERE printing IS NOT NULL)")) {
      rows.next();
      for (int i = 1; i <= 3; i++) assertThat(rows.getLong(i)).isZero();
      for (int i = 4; i <= 6; i++) assertThat(rows.getLong(i)).isEqualTo(1);
      assertThat(rows.getLong(7)).isEqualTo(2);
      assertThat(rows.getLong(8)).isZero();
    }
    try (var c = DriverManager.getConnection(url, user, password);
        var s = c.createStatement()) {
      s.execute(
          "INSERT INTO"
              + " print_batches(id,version,creator_id,status,printer_name,printing_assignee_id,layout_revision,layout_fingerprint,updated_at,confirmed_at,artifact_id)"
              + " VALUES(801,4,12,'CONFIRMED','existing-printer',13,2,'existing-fingerprint',NOW(),NOW(),'old-file')");
      s.execute(
          "INSERT INTO print_batch_items(batch_id,order_number,plate_task_id,mapping_task_id)"
              + " VALUES(801,'PE-MIG-2',7004,7003)");
      s.execute(
          "INSERT INTO print_batch_slots(batch_id,slot_label,filament_id) VALUES(801,'AMS-1',901)");
      s.execute(
          "INSERT INTO"
              + " print_batch_artifacts(id,batch_id,uploader_id,layout_revision,file_name,object_key,expected_size,expires_at,confirmed)"
              + " VALUES('old-file',801,12,2,'old.3mf','production/print-batches/801/old.3mf',100,DATE_ADD(NOW(),INTERVAL"
              + " 1 HOUR),1)");
    }
    var finishingVersion = Flyway.configure().dataSource(url, user, password).target("21").load();
    assertThat(finishingVersion.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(finishingVersion.migrate().migrationsExecuted).isZero();
    try (var c = DriverManager.getConnection(url, user, password);
        var s = c.createStatement();
        var r =
            s.executeQuery(
                "SELECT (SELECT COUNT(*) FROM print_batches WHERE id=801 AND version=4 AND"
                    + " status='CONFIRMED' AND artifact_id='old-file' AND started_at IS NULL AND"
                    + " finished_at IS NULL),(SELECT COUNT(*) FROM print_batch_artifacts WHERE"
                    + " id='old-file' AND confirmed=1),(SELECT COUNT(*) FROM print_batch_items"
                    + " WHERE batch_id=801),(SELECT COUNT(*) FROM production_compensation_settings"
                    + " WHERE id=1 AND enabled=0),(SELECT COUNT(*) FROM"
                    + " production_paid_workers),(SELECT COUNT(*) FROM"
                    + " production_settlements),(SELECT COUNT(*) FROM print_run_results),(SELECT"
                    + " COUNT(*) FROM print_batch_observations),(SELECT COUNT(*) FROM"
                    + " finishing_records),(SELECT COUNT(*) FROM goods_survey_fulfillments)")) {
      r.next();
      for (int i = 1; i <= 4; i++) assertThat(r.getLong(i)).isEqualTo(1);
      for (int i = 5; i <= 9; i++) assertThat(r.getLong(i)).isZero();
      assertThat(r.getLong(10)).isEqualTo(10);
    }
    try (var c = DriverManager.getConnection(url, user, password);
        var s = c.createStatement()) {
      // V28은 아직 보관 중인 주문만 달력상 3개월 기준으로 다시 계산한다.
      // 2096년을 써서 실행 시점과 무관하게 migration 대상임을 보장한다.
      s.execute(
          "UPDATE goods_survey_fulfillments SET delivery_completed_at='2096-01-31 03:00:00',"
              + " delete_after='2096-05-01 03:00:00' WHERE order_number='PE-MIG-2'");
    }
    var shippingVersion = Flyway.configure().dataSource(url, user, password).load();
    assertThat(shippingVersion.migrate().migrationsExecuted).isEqualTo(7);
    assertThat(shippingVersion.migrate().migrationsExecuted).isZero();
    try (var c = DriverManager.getConnection(url, user, password);
        var s = c.createStatement();
        var r =
            s.executeQuery(
                "SELECT (SELECT COUNT(*) FROM goods_survey_fulfillments),(SELECT COUNT(*) FROM"
                    + " shipment_export_batches),(SELECT COUNT(*) FROM"
                    + " shipment_export_items),(SELECT COUNT(*) FROM print_batches WHERE id=801 AND"
                    + " status='CONFIRMED'),(SELECT COUNT(*) FROM goods_order_pets),(SELECT COUNT(*)"
                    + " FROM postal_import_batches),(SELECT COUNT(*) FROM"
                    + " shipment_notification_events),(SELECT COUNT(*) FROM"
                    + " production_payout_batches),(SELECT COUNT(*) FROM production_payout_items),(SELECT COUNT(*) FROM"
                    + " as_cases),(SELECT COUNT(*) FROM as_case_access_grants),(SELECT COUNT(*) FROM"
                    + " as_case_access_grant_assets),(SELECT COUNT(*) FROM goods_survey_fulfillments"
                    + " WHERE order_number='PE-MIG-2' AND delete_after='2096-04-30 03:00:00')")) {
      r.next();
      assertThat(r.getLong(1)).isEqualTo(10);
      assertThat(r.getLong(2)).isZero();
      assertThat(r.getLong(3)).isZero();
      assertThat(r.getLong(4)).isEqualTo(1);
      assertThat(r.getLong(5)).isEqualTo(10);
      assertThat(r.getLong(6)).isZero();
      assertThat(r.getLong(7)).isZero();
      assertThat(r.getLong(8)).isZero();
      assertThat(r.getLong(9)).isZero();
      assertThat(r.getLong(10)).isZero();
      assertThat(r.getLong(11)).isZero();
      assertThat(r.getLong(12)).isZero();
      assertThat(r.getLong(13)).isEqualTo(1);
    }
  }
}
