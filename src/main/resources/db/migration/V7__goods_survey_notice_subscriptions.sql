-- 2차 제작 안내를 받을 이메일.
--
-- 굿즈 신청 정보(goods_survey_fulfillments)와 섞지 않는다. 그쪽은 "굿즈 제작·발송"
-- 목적으로 고지하고 받아 발송 3주 뒤 지우는 데이터라, 목적도 보유 기간도 다르다.
-- 전화번호 해시에 전역 UNIQUE가 걸려 있어 1차 신청자가 다시 신청하면 충돌하기도 한다.
--
-- response_id를 두지 않는 이유: 설문은 "신원 정보를 받지 않고 익명으로 분석한다"고
-- 고지하고 받았다. 응답과 연결해 두면 어떤 사람이 무엇이라 답했는지 식별되어
-- 그 고지가 거짓이 된다. 신청 자격은 요청 시점에만 확인하고 저장하지 않는다.
CREATE TABLE `goods_survey_notice_subscriptions`
(
    `id`              BIGINT        NOT NULL AUTO_INCREMENT,
    `campaign_id`     VARCHAR(50)   NOT NULL,
    -- 본문은 암호화해 저장하고, 중복 확인은 해시로만 한다.
    `email`           VARCHAR(1000) NOT NULL,
    `email_hash`      VARCHAR(88)   NOT NULL,
    `consent_version` VARCHAR(30)   NOT NULL,
    `consented_at`    DATETIME(6)   NOT NULL,
    -- 수신거부는 문의로 받아 사람이 처리한다. 처리한 사실을 남길 곳이 없으면
    -- 다음 발송 때 그대로 다시 나가므로 상태만은 여기에 기록한다.
    `unsubscribed_at` DATETIME(6)   NULL,
    `delete_after`    DATETIME(6)   NOT NULL,
    `created_at`      DATETIME(6)   NULL,
    `updated_at`      DATETIME(6)   NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_goods_survey_notice_subscriptions_email_hash` (`email_hash`),
    KEY `IDX_goods_survey_notice_subscriptions_delete_after` (`delete_after`),
    CONSTRAINT `FK_goods_survey_notice_subscriptions_campaign`
        FOREIGN KEY (`campaign_id`) REFERENCES `goods_survey_campaigns` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
