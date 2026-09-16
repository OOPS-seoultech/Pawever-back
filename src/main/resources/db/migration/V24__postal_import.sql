-- 우체국 접수 내역 붙여넣기.
-- 대표가 우체국에서 받아 온 내역을 주문과 맞춰 송장을 채운다. 무엇을 보고
-- 그렇게 정했는지 되짚을 수 있어야 해서 원문과 판단 결과를 함께 남긴다.

CREATE TABLE postal_import_batches (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  -- 이번에 부친 묶음. 찾는 범위를 이 안으로 가둔다.
  outbound_batch_id BIGINT NOT NULL,
  imported_by BIGINT NOT NULL,
  imported_at DATETIME(6) NOT NULL,
  row_count INT NOT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  KEY ix_postal_import_batches_outbound (outbound_batch_id)
);

CREATE TABLE postal_import_rows (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  batch_id BIGINT NOT NULL,
  line_number INT NOT NULL,
  -- 원문과 이름 표기에는 고객 이름이 들어 있다. 주문 줄과 같은 방식으로 암호화한다.
  raw_line VARCHAR(1000) NULL,
  -- 앞자리 0을 지키려고 문자열로 둔다. 숫자로 바꾸면 번호가 사라진다.
  tracking_number VARCHAR(20) NULL,
  postage_krw INT NULL,
  postal_code VARCHAR(10) NULL,
  recipient_label VARCHAR(1000) NULL,
  status VARCHAR(20) NOT NULL,
  match_kind VARCHAR(30) NULL,
  reason VARCHAR(500) NULL,
  candidate_order_numbers VARCHAR(1000) NULL,
  matched_order_number VARCHAR(20) NULL,
  issues VARCHAR(500) NULL,
  resolved_by BIGINT NULL,
  resolved_at DATETIME(6) NULL,
  resolve_reason VARCHAR(300) NULL,
  committed_at DATETIME(6) NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  KEY ix_postal_import_rows_batch (batch_id),
  KEY ix_postal_import_rows_tracking (tracking_number)
);

-- 우체국이 접수한 사실. 배달 완료와 다르고, 개인정보 보관 시계를 여기서
-- 시작하지 않는다 -- 고객에게 고지한 것은 "배송 완료를 표시한 날부터"다.
ALTER TABLE goods_survey_fulfillments
  ADD COLUMN postal_postage_krw INT NULL,
  ADD COLUMN post_office_accepted_at DATETIME(6) NULL;

-- 같은 송장번호가 두 주문에 붙으면 한쪽 고객은 자기 물건을 추적할 수 없다.
CREATE INDEX ix_goods_orders_tracking_number
  ON goods_survey_fulfillments (tracking_number);
