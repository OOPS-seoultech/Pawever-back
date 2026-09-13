CREATE TABLE shipment_export_batches (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  exported_by BIGINT NOT NULL,
  exported_at DATETIME(6) NOT NULL,
  order_count INT NOT NULL,
  file_base64 LONGTEXT NULL
);
CREATE TABLE shipment_export_items (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  batch_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  order_number VARCHAR(20) NOT NULL,
  export_row_number INT NOT NULL,
  snapshot_json LONGTEXT NULL,
  fingerprint VARCHAR(64) NULL,
  UNIQUE KEY uk_shipment_export_order (order_number),
  UNIQUE KEY uk_shipment_export_row (batch_id, export_row_number)
);
