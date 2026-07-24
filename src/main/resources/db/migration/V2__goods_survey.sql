CREATE TABLE `goods_survey_campaigns` (
    `id`                   VARCHAR(50) NOT NULL,
    `capacity`             INT         NOT NULL,
    `historical_allocated` INT         NOT NULL,
    `starts_at`            DATETIME(6) NOT NULL,
    `ends_at`              DATETIME(6) NOT NULL,
    `created_at`           DATETIME(6) NULL,
    `updated_at`           DATETIME(6) NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `goods_survey_responses` (
    `id`                       CHAR(36)     NOT NULL,
    `campaign_id`              VARCHAR(50)  NOT NULL,
    `questionnaire_version`    VARCHAR(50)  NOT NULL,
    `edit_token_hash`          VARCHAR(88)  NOT NULL,
    `status`                   VARCHAR(30)  NOT NULL,
    `selected_goods`           VARCHAR(30)  NOT NULL,
    `answers_json`             LONGTEXT     NULL,
    `current_question_id`      VARCHAR(30)  NULL,
    `tracking_json`            LONGTEXT     NULL,
    `survey_active_ms`         BIGINT       NULL,
    `question_timings_json`    LONGTEXT     NULL,
    `completed_at`             DATETIME(6)  NULL,
    `reservation_expires_at`   DATETIME(6)  NULL,
    `version`                  BIGINT       NOT NULL DEFAULT 0,
    `created_at`               DATETIME(6)  NULL,
    `updated_at`               DATETIME(6)  NULL,
    PRIMARY KEY (`id`),
    KEY `IDX_goods_survey_responses_allocation`
        (`campaign_id`, `status`, `reservation_expires_at`),
    CONSTRAINT `FK_goods_survey_responses_campaign`
        FOREIGN KEY (`campaign_id`) REFERENCES `goods_survey_campaigns` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `goods_survey_stories` (
    `id`                       BIGINT       NOT NULL AUTO_INCREMENT,
    `response_id`              CHAR(36)     NOT NULL,
    `story_json`               MEDIUMTEXT   NOT NULL,
    `analysis_agreed`          BOOLEAN      NOT NULL,
    `publish_agreed`           BOOLEAN      NOT NULL,
    `review_contact_agreed`    BOOLEAN      NOT NULL,
    `interview_agreed`         BOOLEAN      NOT NULL,
    `consent_version`          VARCHAR(30)  NOT NULL,
    `consented_at`             DATETIME(6)  NOT NULL,
    `created_at`               DATETIME(6)  NULL,
    `updated_at`               DATETIME(6)  NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_goods_survey_stories_response_id` (`response_id`),
    CONSTRAINT `FK_goods_survey_stories_response`
        FOREIGN KEY (`response_id`) REFERENCES `goods_survey_responses` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `goods_survey_fulfillments` (
    `id`                        BIGINT        NOT NULL AUTO_INCREMENT,
    `response_id`               CHAR(36)      NOT NULL,
    `idempotency_key`           VARCHAR(80)   NOT NULL,
    `conversion_event_id`       VARCHAR(80)   NOT NULL,
    `tracking_json`             LONGTEXT      NOT NULL,
    `goods_type`                VARCHAR(30)   NOT NULL,
    `custom_goods`              VARCHAR(500)  NULL,
    `pet_name`                  VARCHAR(1000) NOT NULL,
    `guardian_name`             VARCHAR(1000) NOT NULL,
    `phone`                     VARCHAR(1000) NOT NULL,
    `phone_hash`                VARCHAR(88)   NOT NULL,
    `postal_code`               VARCHAR(1000) NOT NULL,
    `address`                   VARCHAR(2000) NOT NULL,
    `address_detail`            VARCHAR(2000) NULL,
    `privacy_consent_version`   VARCHAR(30)   NOT NULL,
    `privacy_consented_at`      DATETIME(6)   NOT NULL,
    `delivery_completed_at`     DATETIME(6)   NULL,
    `delete_after`              DATETIME(6)   NULL,
    `created_at`                DATETIME(6)   NULL,
    `updated_at`                DATETIME(6)   NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_goods_survey_fulfillments_response_id` (`response_id`),
    UNIQUE KEY `UK_goods_survey_fulfillments_idempotency_key` (`idempotency_key`),
    UNIQUE KEY `UK_goods_survey_fulfillments_conversion_event_id` (`conversion_event_id`),
    UNIQUE KEY `UK_goods_survey_fulfillments_phone_hash` (`phone_hash`),
    CONSTRAINT `FK_goods_survey_fulfillments_response`
        FOREIGN KEY (`response_id`) REFERENCES `goods_survey_responses` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `goods_survey_photos` (
    `id`                  CHAR(36)     NOT NULL,
    `response_id`         CHAR(36)     NOT NULL,
    `client_file_id`      CHAR(36)     NOT NULL,
    `object_key`          VARCHAR(500) NOT NULL,
    `content_type`        VARCHAR(50)  NOT NULL,
    `expected_size`       BIGINT       NOT NULL,
    `actual_size`         BIGINT       NULL,
    `status`              VARCHAR(20)  NOT NULL,
    `upload_expires_at`   DATETIME(6)  NOT NULL,
    `confirmed_at`        DATETIME(6)  NULL,
    `created_at`          DATETIME(6)  NULL,
    `updated_at`          DATETIME(6)  NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_goods_survey_photos_object_key` (`object_key`),
    UNIQUE KEY `UK_goods_survey_photos_response_client`
        (`response_id`, `client_file_id`),
    KEY `IDX_goods_survey_photos_response_status` (`response_id`, `status`),
    CONSTRAINT `FK_goods_survey_photos_response`
        FOREIGN KEY (`response_id`) REFERENCES `goods_survey_responses` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `goods_survey_campaigns` (
    `id`,
    `capacity`,
    `historical_allocated`,
    `starts_at`,
    `ends_at`,
    `created_at`,
    `updated_at`
) VALUES (
    'goods-2026-07',
    100,
    27,
    '2026-07-22 15:00:00',
    '2026-08-05 14:59:59',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
);
