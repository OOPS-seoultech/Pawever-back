ALTER TABLE print_batches ADD COLUMN started_at DATETIME(6) NULL, ADD COLUMN finished_at DATETIME(6) NULL;
CREATE TABLE print_run_results (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, batch_id BIGINT NOT NULL, order_number VARCHAR(20) NOT NULL,
 task_id BIGINT NOT NULL UNIQUE, attempt INT NOT NULL, result VARCHAR(20) NOT NULL, note VARCHAR(1000) NOT NULL,
 actor_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL, KEY idx_print_result_batch(batch_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE print_batch_observations (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, batch_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
 note VARCHAR(1000) NOT NULL, purge_grams DECIMAL(10,2) NULL, issues_json LONGTEXT NOT NULL,
 created_at DATETIME(6) NOT NULL, KEY idx_print_observation_batch(batch_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE finishing_records (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_number VARCHAR(20) NOT NULL, task_id BIGINT NOT NULL UNIQUE,
 attempt INT NOT NULL, stage VARCHAR(30) NOT NULL, decision VARCHAR(20) NOT NULL, checks VARCHAR(300) NOT NULL,
 note VARCHAR(1000) NOT NULL, reason_code VARCHAR(40) NOT NULL, rework_stage VARCHAR(30) NOT NULL,
 settlement_status VARCHAR(30) NOT NULL, actor_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL,
 KEY idx_finishing_order(order_number,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE production_compensation_settings (
 id BIGINT PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0, enabled BOOLEAN NOT NULL DEFAULT FALSE,
 revision BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO production_compensation_settings(id,version,enabled,revision) VALUES(1,0,FALSE,0);
CREATE TABLE production_paid_workers (account_id BIGINT PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE production_settlements (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_number VARCHAR(20) NOT NULL UNIQUE, qc_task_id BIGINT NOT NULL UNIQUE,
 worker_id BIGINT NOT NULL, amount_krw INT NOT NULL, created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
