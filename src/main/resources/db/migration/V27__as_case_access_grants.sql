-- 완료 주문의 AS 열람은 기존 제작 권한과 분리한다. 원본 키는 이 표에 저장하지 않는다.
CREATE TABLE as_cases (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_number VARCHAR(20) NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  closed_by BIGINT NULL,
  closed_at DATETIME(6) NULL,
  KEY ix_as_case_order(order_number, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE as_case_access_grants (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  as_case_id BIGINT NOT NULL,
  recipient_id BIGINT NOT NULL,
  activated_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NULL,
  revoked_by BIGINT NULL,
  replaced_grant_id BIGINT NULL,
  granted_by BIGINT NOT NULL,
  KEY ix_as_grant_recipient(as_case_id, recipient_id, id),
  KEY ix_as_grant_expiry(expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE as_case_access_grant_assets (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  grant_id BIGINT NOT NULL,
  asset_type VARCHAR(30) NOT NULL,
  asset_id VARCHAR(80) NOT NULL,
  UNIQUE KEY uq_as_grant_asset(grant_id, asset_type, asset_id),
  KEY ix_as_grant_asset_lookup(grant_id, asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
