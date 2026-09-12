-- Additive first-flow foundation. No historical production stage or pickup location is inferred.
-- status is retained for existing queries; all writes synchronize lifecycle in the order aggregate.
ALTER TABLE admin_accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN permissions_changed_at DATETIME(6) NULL;
ALTER TABLE goods_survey_fulfillments
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN production_stage VARCHAR(30) NULL,
    ADD COLUMN lifecycle_order_status VARCHAR(20) NULL,
    ADD COLUMN lifecycle_payment_status VARCHAR(20) NULL,
    ADD COLUMN lifecycle_shipment_status VARCHAR(40) NULL,
    ADD COLUMN payment_confirmed_by BIGINT NULL,
    ADD COLUMN confirmed_amount_krw INT NULL,
    ADD COLUMN workflow_touched_at DATETIME(6) NULL;

UPDATE goods_survey_fulfillments SET
    lifecycle_order_status = CASE status WHEN 'CANCELED' THEN 'CANCELED' WHEN 'PAYMENT_FAILED' THEN 'CANCELED'
        WHEN 'PAYMENT_EXPIRED' THEN 'EXPIRED' WHEN 'PICKED_UP' THEN 'COMPLETED' ELSE 'ACTIVE' END,
    lifecycle_payment_status = CASE status WHEN 'PAYMENT_PENDING' THEN 'PENDING' WHEN 'PAYMENT_FAILED' THEN 'FAILED'
        WHEN 'PAYMENT_EXPIRED' THEN 'EXPIRED' WHEN 'CANCEL_FAILED' THEN 'REFUND_PENDING'
        WHEN 'CANCELED' THEN CASE WHEN paid_at IS NULL THEN 'NOT_REQUIRED' ELSE 'REFUNDED' END
        ELSE CASE WHEN paid_at IS NULL THEN 'NOT_REQUIRED' ELSE 'CONFIRMED' END END,
    lifecycle_shipment_status = CASE status WHEN 'SHIPPED' THEN 'ACCEPTED' WHEN 'PICKED_UP' THEN 'PICKED_UP' ELSE 'NOT_READY' END;

CREATE TABLE staff_work_roles (
    account_id BIGINT NOT NULL, work_role VARCHAR(30) NOT NULL,
    PRIMARY KEY(account_id, work_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE account_permission_overrides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, account_id BIGINT NOT NULL,
    permission_key VARCHAR(60) NOT NULL, allowed BOOLEAN NOT NULL,
    expires_at DATETIME(6) NULL, reason VARCHAR(300) NOT NULL,
    UNIQUE KEY uk_account_permission(account_id, permission_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE production_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, order_number VARCHAR(20) NOT NULL,
    stage VARCHAR(30) NOT NULL, attempt INT NOT NULL DEFAULT 1, assignee_id BIGINT NULL,
    status VARCHAR(20) NOT NULL, started_at DATETIME(6) NULL, completed_at DATETIME(6) NULL,
    UNIQUE KEY uk_order_stage_attempt(order_number, stage, attempt),
    KEY idx_my_tasks(assignee_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE production_artifacts (
    id VARCHAR(255) PRIMARY KEY, order_number VARCHAR(20) NOT NULL, task_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL, kind VARCHAR(30) NOT NULL, file_name VARCHAR(200) NOT NULL,
    content_type VARCHAR(100) NOT NULL, object_key VARCHAR(500) NOT NULL,
    expected_size BIGINT NOT NULL, confirmed BOOLEAN NOT NULL, expires_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_artifact_object(object_key), KEY idx_artifact_order(order_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE workflow_issues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, order_number VARCHAR(20) NOT NULL, code VARCHAR(40) NOT NULL,
    UNIQUE KEY uk_order_issue(order_number,code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE workflow_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, resource VARCHAR(80) NOT NULL, actor_id BIGINT NOT NULL,
    action VARCHAR(80) NOT NULL, before_value VARCHAR(500) NULL, after_value VARCHAR(500) NULL,
    reason VARCHAR(300) NULL, created_at DATETIME(6) NOT NULL, KEY idx_workflow_audit(resource,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE workflow_commands (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, actor_id BIGINT NOT NULL, command_key VARCHAR(100) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL, result_json LONGTEXT NOT NULL, order_number VARCHAR(20) NULL,
    UNIQUE KEY uk_workflow_command(actor_id,command_key), KEY idx_workflow_command_order(order_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE workflow_settings (
    id BIGINT PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0, modeling BIGINT NULL, review BIGINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO workflow_settings(id,version) VALUES(1,0);

-- No account is promoted to OWNER automatically. Select the existing account explicitly at rollout.
-- Review report: SELECT order_number,status FROM goods_survey_fulfillments
-- WHERE status='IN_PRODUCTION' AND production_stage IS NULL;
