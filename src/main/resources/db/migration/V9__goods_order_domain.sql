-- 신청을 주문으로 다룬다.
--
-- 지금까지는 신청 정보를 받아 두고 사람이 만들어 보내는 것이 전부였다.
-- 값을 받기 시작하면 결제·제작·발송·취소가 한 줄기로 이어지고, 어느 단계에
-- 있는지와 어떻게 여기까지 왔는지를 남겨야 한다.

-- 연도별 주문번호 채번기. PE-2026-000001 처럼 해마다 1부터 센다.
-- 기존 주문에서 최대값을 찾는 방식은 동시에 신청하면 같은 번호를 준다.
CREATE TABLE `goods_order_sequences`
(
    `sequence_year` INT NOT NULL,
    `last_number`   INT NOT NULL,
    PRIMARY KEY (`sequence_year`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 상태가 바뀐 기록. 주문 행에는 지금 상태만 남아, 언제 누가 왜 바꿨는지는
-- 여기에 쌓는다. 취소나 환불로 다투게 되면 지금 상태만으로는 설명할 수 없다.
CREATE TABLE `goods_order_status_changes`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `response_id` VARCHAR(36) NOT NULL,
    -- 처음 만들어진 주문은 이전 상태가 없다.
    `from_status` VARCHAR(30) NULL,
    `to_status`   VARCHAR(30) NOT NULL,
    `changed_at`  DATETIME(6) NOT NULL,
    -- 사람이 바꿨으면 그 계정, 시스템이 바꿨으면 비어 있다.
    `changed_by`  VARCHAR(100) NULL,
    `memo`        VARCHAR(300) NULL,
    PRIMARY KEY (`id`),
    KEY `IDX_goods_order_status_changes_response` (`response_id`, `changed_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

ALTER TABLE `goods_survey_fulfillments`
    -- 고객에게 읽어 줄 번호. 응답 식별자(UUID)는 전화로 부르기 어렵다.
    ADD COLUMN `order_number` VARCHAR(20) NULL AFTER `response_id`,
    ADD COLUMN `status` VARCHAR(30) NOT NULL DEFAULT 'LEGACY_FREE' AFTER `order_number`,
    ADD COLUMN `list_price_krw` INT NOT NULL DEFAULT 0 AFTER `survey_participant`,
    ADD COLUMN `discount_amount_krw` INT NOT NULL DEFAULT 0 AFTER `list_price_krw`,
    ADD COLUMN `promotion_name` VARCHAR(100) NULL AFTER `discount_amount_krw`,
    ADD COLUMN `payment_amount_krw` INT NOT NULL DEFAULT 0 AFTER `promotion_name`,
    ADD COLUMN `payment_key` VARCHAR(200) NULL AFTER `payment_amount_krw`,
    ADD COLUMN `payment_method` VARCHAR(30) NULL AFTER `payment_key`,
    ADD COLUMN `payment_expires_at` DATETIME(6) NULL AFTER `payment_method`,
    ADD COLUMN `cancel_reason` VARCHAR(300) NULL AFTER `paid_at`,
    ADD COLUMN `tracking_company` VARCHAR(50) NULL AFTER `cancel_reason`,
    ADD COLUMN `tracking_number` VARCHAR(50) NULL AFTER `tracking_company`,
    -- 광고성 정보 수신 동의. 개인정보 수집·이용 동의와 나눠 받는다.
    ADD COLUMN `marketing_consent` BOOLEAN NOT NULL DEFAULT FALSE AFTER `tracking_number`,
    ADD COLUMN `marketing_consented_at` DATETIME(6) NULL AFTER `marketing_consent`,
    ADD COLUMN `marketing_consent_version` VARCHAR(30) NULL AFTER `marketing_consented_at`;

-- 기존 100건은 1차 무료 체험단이다. 결제라는 것이 없던 때의 신청이라 위 흐름
-- 어디에도 맞지 않는다. 결제 완료로 두면 받지도 않은 돈을 받은 것으로 세고,
-- 대기로 두면 30분 만료 대상이 된다. 상태를 나눠 두고 관리자에서 걸러 본다.
-- (status 는 기본값 LEGACY_FREE 로 이미 채워졌다.)
--
-- 주문번호도 소급해서 매긴다. 관리자 화면이 번호로 주문을 부르는데 1차만
-- 비어 있으면 그 화면에서 다룰 수 없다. 신청 순서대로 PE-2026-000001 부터 준다.
SET @row_number = 0;
UPDATE `goods_survey_fulfillments`
SET `order_number` = CONCAT('PE-2026-', LPAD((@row_number := @row_number + 1), 6, '0'))
WHERE `order_number` IS NULL
ORDER BY `id`;

-- 소급한 만큼 채번기를 앞으로 당겨 둔다. 그러지 않으면 다음 주문이
-- PE-2026-000001 을 다시 받아 UNIQUE 에 걸린다.
INSERT INTO `goods_order_sequences` (`sequence_year`, `last_number`)
SELECT 2026, COUNT(*) FROM `goods_survey_fulfillments`;

-- 번호를 다 채운 뒤에 제약을 건다.
ALTER TABLE `goods_survey_fulfillments`
    MODIFY COLUMN `order_number` VARCHAR(20) NOT NULL,
    ADD UNIQUE KEY `UK_goods_survey_fulfillments_order_number` (`order_number`);

-- 어제 넣은 applied_price_krw 는 payment_amount_krw 로 갈음한다.
-- 1차는 무상이라 값이 모두 0 이었다.
ALTER TABLE `goods_survey_fulfillments`
    DROP COLUMN `applied_price_krw`;

-- 만료 배치가 매일 훑는 기준이다.
CREATE INDEX `IDX_goods_survey_fulfillments_status_payment_expires`
    ON `goods_survey_fulfillments` (`status`, `payment_expires_at`);
