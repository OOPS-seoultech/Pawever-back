-- 기존 적립 원장은 그대로 두고, 실제 은행 이체 기록만 별도 원장으로 관리한다.
ALTER TABLE production_settlements
  ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID';

CREATE TABLE production_payout_batches (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  beneficiary_id BIGINT NOT NULL,
  gross_krw INT NOT NULL,
  deduction_krw INT NOT NULL,
  net_krw INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  prepared_by BIGINT NOT NULL,
  prepared_at DATETIME(6) NOT NULL,
  paid_by BIGINT NULL,
  paid_at DATETIME(6) NULL,
  transfer_reference VARCHAR(300) NULL,
  KEY ix_payout_batch_beneficiary(beneficiary_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE production_payout_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payout_batch_id BIGINT NOT NULL,
  settlement_id BIGINT NOT NULL UNIQUE,
  amount_krw INT NOT NULL,
  KEY ix_payout_item_batch(payout_batch_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
