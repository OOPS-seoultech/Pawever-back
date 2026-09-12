-- Existing orders, mapping snapshots and staff assignments remain unchanged.
ALTER TABLE workflow_settings ADD COLUMN printing BIGINT NULL;
CREATE TABLE print_batches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    creator_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    printer_name VARCHAR(80) NOT NULL,
    printing_assignee_id BIGINT NULL,
    layout_revision INT NOT NULL,
    layout_fingerprint VARCHAR(64) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    artifact_id VARCHAR(36) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE print_batch_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    order_number VARCHAR(20) NOT NULL,
    plate_task_id BIGINT NOT NULL,
    mapping_task_id BIGINT NOT NULL,
    UNIQUE KEY uk_print_item_task(plate_task_id),
    KEY idx_print_item_batch(batch_id,id),
    KEY idx_print_item_order(order_number,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE print_batch_slots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    slot_label VARCHAR(30) COLLATE utf8mb4_bin NOT NULL,
    filament_id BIGINT NOT NULL,
    UNIQUE KEY uk_print_slot_label(batch_id,slot_label),
    UNIQUE KEY uk_print_slot_filament(batch_id,filament_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE print_batch_artifacts (
    id VARCHAR(36) PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    layout_revision INT NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    expected_size BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uk_print_artifact_object(object_key),
    KEY idx_print_artifact_batch(batch_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
