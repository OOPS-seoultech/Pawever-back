-- Additive only: real inventory and existing orders are not seeded or enrolled.
-- Preserve full before/after snapshots even when an order has many parts.
ALTER TABLE workflow_audit_events
    MODIFY before_value LONGTEXT NULL,
    MODIFY after_value LONGTEXT NULL;

CREATE TABLE filaments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    spool_id VARCHAR(64) NOT NULL,
    color_name VARCHAR(80) NOT NULL,
    material VARCHAR(40) NOT NULL,
    finish VARCHAR(40) NOT NULL,
    manufacturer VARCHAR(100) NOT NULL,
    source VARCHAR(300) NOT NULL,
    price_krw BIGINT NULL,
    remaining_grams BIGINT NOT NULL,
    active BOOLEAN NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_filament_spool(spool_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_filament_mappings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(20) NOT NULL,
    task_id BIGINT NOT NULL,
    modeling_attempt INT NOT NULL,
    part_name VARCHAR(60) NOT NULL,
    part_key VARCHAR(60) COLLATE utf8mb4_bin NOT NULL,
    filament_id BIGINT NOT NULL,
    spool_id VARCHAR(64) NOT NULL,
    color_name VARCHAR(80) NOT NULL,
    material VARCHAR(40) NOT NULL,
    finish VARCHAR(40) NOT NULL,
    saved_by BIGINT NOT NULL,
    saved_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    UNIQUE KEY uk_filament_mapping_part(task_id,part_key),
    KEY idx_filament_mapping_order(order_number,id),
    KEY idx_filament_mapping_filament(filament_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
