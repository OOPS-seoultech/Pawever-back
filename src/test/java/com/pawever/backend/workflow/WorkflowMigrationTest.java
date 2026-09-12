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
  void v17PreservesHistoricalOrdersAndDoesNotInventTasksOrOwners() throws Exception {
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
    var flyway = Flyway.configure().dataSource(url, user, password).load();
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
    }
  }
}
