-- 담당자가 고객 정보에 손댄 기록.
--
-- 사진을 내려받거나 주소 전체를 열어 본 일은 화면에 흔적이 남지 않는다.
-- 남기지 않으면 정보가 밖으로 나갔을 때 누구를 거쳐 나갔는지 알 방법이 없다.
--
-- 무엇을 봤는지는 남기되 본 내용은 남기지 않는다. 이력이 또 하나의
-- 개인정보 보관처가 되면 안 된다.
CREATE TABLE `admin_access_logs`
(
    `id`               BIGINT      NOT NULL AUTO_INCREMENT,
    `admin_account_id` BIGINT      NOT NULL,
    -- PHOTO_DOWNLOAD, ADDRESS_VIEW 처럼 무엇을 했는지.
    `action`           VARCHAR(40) NOT NULL,
    -- 사람이 바로 읽을 수 있도록 주문번호로 남긴다.
    `order_number`     VARCHAR(20) NOT NULL,
    `accessed_at`      DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `IDX_admin_access_logs_order` (`order_number`, `accessed_at`),
    KEY `IDX_admin_access_logs_account` (`admin_account_id`, `accessed_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
