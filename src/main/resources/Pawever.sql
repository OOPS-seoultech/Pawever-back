-- ============================================================
-- Pawever Database Schema (MariaDB)
-- JPA Entity 구조와 동기화된 DDL
-- hibernate ddl-auto: update 사용 시 자동 생성되지만
-- 수동 생성이 필요한 경우 이 스크립트를 사용
-- ============================================================

-- 1. 회원 (users) - BaseTimeEntity 상속 → created_at, updated_at
CREATE TABLE `users` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT,
    `name`              VARCHAR(500)    NULL,
    `nickname`          VARCHAR(255)    NULL,
    `phone`             VARCHAR(500)    NULL,
    `phone_hash`        VARCHAR(255)    NULL UNIQUE,
    `email`             VARCHAR(500)    NULL,
    `gender`            VARCHAR(20)     NULL,
    `birthday`          VARCHAR(10)     NULL,
    `birth_year`        VARCHAR(10)     NULL,
    `age_range`         VARCHAR(20)     NULL,
    `kakao_id`          VARCHAR(255)    NULL,
    `naver_id`          VARCHAR(255)    NULL,
    `profile_image_url` VARCHAR(255)    NULL,
    `selected_pet_id`   BIGINT          NULL,
    `onboarding_complete` BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '온보딩(서비스 상 회원가입) 완료 여부',
    `referral_type`     VARCHAR(20)     NULL     COMMENT 'FRIEND / THREADS / INSTAGRAM / OFFLINE / OTHER',
    `referral_memo`     VARCHAR(255)    NULL,
    `deleted_at`        DATETIME(6)     NULL,
    `notification_agreed_at` DATETIME(6) NULL COMMENT '알림(푸시) 수신 동의 시각, null=미동의',
    `marketing_agreed_at`    DATETIME(6) NULL COMMENT '마케팅 수신 동의 시각, null=미동의',
    `fcm_token`         VARCHAR(255)    NULL COMMENT 'FCM 디바이스 토큰',
    `created_at`        DATETIME(6)     NULL,
    `updated_at`        DATETIME(6)     NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 동물 종류 (animal_types)
CREATE TABLE `animal_types` (
    `id`    BIGINT          NOT NULL AUTO_INCREMENT,
    `name`  VARCHAR(255)    NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 품종 (breeds)
CREATE TABLE `breeds` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT,
    `animal_type_id`  BIGINT          NOT NULL,
    `name`            VARCHAR(255)    NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_animal_types_TO_breeds`
        FOREIGN KEY (`animal_type_id`) REFERENCES `animal_types` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 반려동물 (pets)
CREATE TABLE `pets` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT,
    `breed_id`          BIGINT          NULL,
    `name`              VARCHAR(255)    NOT NULL,
    `birth_date`        DATE            NULL,
    `gender`            VARCHAR(10)     NULL        COMMENT 'MALE / FEMALE',
    `weight`            FLOAT           NULL,
    `is_neutered`       BOOLEAN         DEFAULT FALSE,
    `invite_code`       VARCHAR(255)    NULL,
    `profile_image_url` VARCHAR(255)    NULL,
    `emergency_mode`    BOOLEAN         DEFAULT FALSE,
    `lifecycle_status`  VARCHAR(20)     NOT NULL    COMMENT 'BEFORE_FAREWELL / AFTER_FAREWELL',
    `death_date`        DATETIME(6)     NULL,
    `created_at`        DATETIME(6)     NULL,
    `updated_at`        DATETIME(6)     NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_pets_invite_code` (`invite_code`),
    CONSTRAINT `FK_breeds_TO_pets`
        FOREIGN KEY (`breed_id`) REFERENCES `breeds` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 회원_반려동물 (user_pets)
CREATE TABLE `user_pets` (
    `id`                    BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`               BIGINT      NOT NULL,
    `pet_id`                BIGINT      NOT NULL,
    `is_owner`              BOOLEAN     DEFAULT FALSE,
    `memorial_last_read_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '추모관 마지막 읽음 시각',
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_users_TO_user_pets`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `FK_pets_TO_user_pets`
        FOREIGN KEY (`pet_id`) REFERENCES `pets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5-1. 만료된 초대코드 (pet_expired_invite_codes)
CREATE TABLE `pet_expired_invite_codes` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `pet_id`        BIGINT      NOT NULL,
    `invite_code`   VARCHAR(255) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_pets_TO_pet_expired_invite_codes`
        FOREIGN KEY (`pet_id`) REFERENCES `pets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. 추억남기기 미션 (missions) - 발자국 남기기
CREATE TABLE `missions` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT,
    `category`              VARCHAR(50)     NULL     COMMENT '추억 남기기 / 음성 녹음 / 마음 전하기',
    `name`                  VARCHAR(255)    NOT NULL,
    `description`           TEXT            NULL     COMMENT '부연 설명',
    `action_guide`          TEXT            NULL     COMMENT '행동하는 법',
    `illustration_prompt`   TEXT            NULL     COMMENT '일러스트 AI 프롬프트',
    `order_index`           INT             NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. 반려동물_미션 (pet_missions)
CREATE TABLE `pet_missions` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `pet_id`        BIGINT      NOT NULL,
    `mission_id`    BIGINT      NOT NULL,
    `completed`     BOOLEAN     DEFAULT FALSE,
    `completed_at`  DATETIME(6) NULL,
    `image_url`     VARCHAR(255) NULL,
    `media_url`     VARCHAR(255) NULL,
    `media_type`    VARCHAR(20) NULL,
    `media_format`  VARCHAR(20) NULL,
    `media_size_bytes` BIGINT NULL,
    `media_duration_sec` INT NULL,
    `media_waveform` TEXT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_pets_TO_pet_missions`
        FOREIGN KEY (`pet_id`) REFERENCES `pets` (`id`),
    CONSTRAINT `FK_missions_TO_pet_missions`
        FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. 미리 살펴보기 진행 상태 (farewell_preview_progresses)
-- current_step: 현재 보고 있는 메인 스텝 번호 (1~5)
-- entered_steps: 진입한 메인 스텝 번호 목록 (JSON 배열)
-- completed_main_steps: 완료된 메인 스텝 번호 목록 (1=이별방법, 4=물건정리)
-- resting_completed_sub_step_numbers: 안치준비 완료 하위단계 번호 (1~5: 다음으로, 6: 6단계 열기, 7: 6단계 완료)
-- resting_step2_checked_item_numbers: 안치준비 2단계 체크한 준비물 번호 (1:담요/이불, 2:물티슈/거즈, 3:배변패드/천, 4:아이스팩, 5:목받침수건, 6:위생장갑)
-- administration_completed_sub_step_numbers: 행정처리 완료 하위단계 번호 (1~5)
-- belongings_selected_option_numbers: 물건정리 선택 옵션 번호 (1~4)
-- support_completed_sub_step_numbers: 지원사업 완료 하위단계 번호 (1~4: 토글, 5: 최종확인)
CREATE TABLE `farewell_preview_progresses` (
    `id`                                        BIGINT      NOT NULL AUTO_INCREMENT,
    `pet_id`                                    BIGINT      NOT NULL,
    `has_completed_guide`                       BOOLEAN     NOT NULL DEFAULT FALSE,
    `current_step`                              INT         NOT NULL DEFAULT 1,
    `entered_steps`                             TEXT        NOT NULL,
    `completed_main_steps`                      TEXT        NOT NULL,
    `resting_completed_sub_step_numbers`        TEXT        NOT NULL,
    `resting_step2_checked_item_numbers`        TEXT        NOT NULL,
    `administration_completed_sub_step_numbers` TEXT        NOT NULL,
    `belongings_selected_option_numbers`        TEXT        NOT NULL,
    `support_completed_sub_step_numbers`        TEXT        NOT NULL,
    `created_at`                                DATETIME(6) NULL,
    `updated_at`                                DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_farewell_preview_progresses_pet_id` (`pet_id`),
    CONSTRAINT `FK_pets_TO_farewell_preview_progresses`
        FOREIGN KEY (`pet_id`) REFERENCES `pets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8-1. 긴급 대처 모드 진행 상태 (emergency_progresses)
CREATE TABLE `emergency_progresses` (
    `id`                           BIGINT      NOT NULL AUTO_INCREMENT,
    `pet_id`                       BIGINT      NOT NULL,
    `resting_active_step_number`   INT         NOT NULL DEFAULT 1,
    `resting_completed_step_count` INT         NOT NULL DEFAULT 0,
    `funeral_company_completed`    BOOLEAN     NOT NULL DEFAULT FALSE,
    `created_at`                   DATETIME(6) NULL,
    `updated_at`                   DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_emergency_progresses_pet_id` (`pet_id`),
    CONSTRAINT `FK_pets_TO_emergency_progresses`
        FOREIGN KEY (`pet_id`) REFERENCES `pets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. 댓글 (comments)
CREATE TABLE `comments` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT      NULL,
    `pet_id`        BIGINT      NOT NULL,
    `content`       TEXT        NOT NULL,
    `created_at`    DATETIME(6) NULL,
    `updated_at`    DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_users_TO_comments`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    CONSTRAINT `FK_pets_TO_comments`
        FOREIGN KEY (`pet_id`) REFERENCES `pets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10-1. 댓글 신고 사유 (report_reasons)
CREATE TABLE `report_reasons` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT,
    `name`          VARCHAR(100)    NOT NULL,
    `order_index`   INT             NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10-2. 댓글 신고 (comment_reports)
CREATE TABLE `comment_reports` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `comment_id`    BIGINT      NOT NULL,
    `reporter_id`   BIGINT      NOT NULL,
    `custom_text`   TEXT        NULL,
    `created_at`    DATETIME(6) NULL,
    `updated_at`    DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_comments_TO_comment_reports`
        FOREIGN KEY (`comment_id`) REFERENCES `comments` (`id`),
    CONSTRAINT `FK_users_TO_comment_reports`
        FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10-3. 댓글 신고-사유 매핑 (comment_report_reasons)
CREATE TABLE `comment_report_reasons` (
    `comment_report_id` BIGINT NOT NULL,
    `report_reason_id`  BIGINT NOT NULL,
    PRIMARY KEY (`comment_report_id`, `report_reason_id`),
    CONSTRAINT `FK_comment_reports_TO_comment_report_reasons`
        FOREIGN KEY (`comment_report_id`) REFERENCES `comment_reports` (`id`),
    CONSTRAINT `FK_report_reasons_TO_comment_report_reasons`
        FOREIGN KEY (`report_reason_id`) REFERENCES `report_reasons` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11. 장례업체 (funeral_companies)
CREATE TABLE `funeral_companies` (
    `id`                      BIGINT          NOT NULL AUTO_INCREMENT,
    `name`                    VARCHAR(255)    NOT NULL,
    `english_name`            VARCHAR(255)    NULL,
    `thumbnail_url`           VARCHAR(255)    NULL,
    `naver_map_url`           VARCHAR(255)    NULL,
    `kakao_map_url`           VARCHAR(255)    NULL,
    `location`                VARCHAR(255)    NULL,
    `latitude`                DOUBLE          NULL,
    `longitude`               DOUBLE          NULL,
    `phone`                   VARCHAR(20)     NULL,
    `email`                   VARCHAR(255)    NULL,
    `basic_product_name`      VARCHAR(255)    NULL,
    `basic_product_price`     INT             NULL,
    `operating_hours`         VARCHAR(255)    NULL,
    `website_url`             VARCHAR(255)    NULL,
    `introduction`            TEXT            NULL,
    `guide_text`              TEXT            NULL,
    `service_description`     TEXT            NULL,
    `full_observation`        BOOLEAN         DEFAULT FALSE,
    `open24_hours`            BOOLEAN         DEFAULT FALSE,
    `pickup_service`          BOOLEAN         DEFAULT FALSE,
    `memorial_stone`          BOOLEAN         DEFAULT FALSE,
    `private_memorial_room`   BOOLEAN         DEFAULT FALSE,
    `ossuary`                 BOOLEAN         DEFAULT FALSE,
    `free_basic_urn`          BOOLEAN         DEFAULT FALSE,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11-1. 장례업체 이미지 (funeral_company_images) - @ElementCollection
CREATE TABLE `funeral_company_images` (
    `funeral_company_id`  BIGINT          NOT NULL,
    `image_url`           VARCHAR(255)    NOT NULL,
    PRIMARY KEY (`funeral_company_id`, `image_url`),
    CONSTRAINT `FK_funeral_companies_TO_funeral_company_images`
        FOREIGN KEY (`funeral_company_id`) REFERENCES `funeral_companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 12. 반려동물_장례업체 (pet_funeral_companies)
CREATE TABLE `pet_funeral_companies` (
    `id`                  BIGINT          NOT NULL AUTO_INCREMENT,
    `pet_id`              BIGINT          NOT NULL,
    `funeral_company_id`  BIGINT          NOT NULL,
    `type`                VARCHAR(10)     NOT NULL    COMMENT 'SAVED / BLOCKED',
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_pets_TO_pet_funeral_companies`
        FOREIGN KEY (`pet_id`) REFERENCES `pets` (`id`),
    CONSTRAINT `FK_funeral_companies_TO_pet_funeral_companies`
        FOREIGN KEY (`funeral_company_id`) REFERENCES `funeral_companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. 후기 (reviews)
CREATE TABLE `reviews` (
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`             BIGINT      NOT NULL,
    `pet_id`              BIGINT      NOT NULL,
    `funeral_company_id`  BIGINT      NOT NULL,
    `rating`              INT         NOT NULL,
    `content`             TEXT        NULL,
    `created_at`          DATETIME(6) NULL,
    `updated_at`          DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_users_TO_reviews`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `FK_pets_TO_reviews`
        FOREIGN KEY (`pet_id`) REFERENCES `pets` (`id`),
    CONSTRAINT `FK_funeral_companies_TO_reviews`
        FOREIGN KEY (`funeral_company_id`) REFERENCES `funeral_companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13-1. 장례업체 후기 이미지 (review_images)
CREATE TABLE `review_images` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT,
    `review_id`     BIGINT          NOT NULL,
    `image_url`     VARCHAR(255)    NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_reviews_TO_review_images`
        FOREIGN KEY (`review_id`) REFERENCES `reviews` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. 자주 묻는 질문 (faqs)
CREATE TABLE `faqs` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT,
    `question`      TEXT            NOT NULL,
    `answer`        TEXT            NOT NULL,
    `detail_answer` TEXT            NULL,
    `order_index`   INT             NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15. 서비스 후기 (service_reviews) - BaseTimeEntity
CREATE TABLE `service_reviews` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT          NOT NULL,
    `content`       TEXT            NULL,
    `created_at`    DATETIME(6)     NULL,
    `updated_at`    DATETIME(6)     NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_users_TO_service_reviews`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 16. 서비스 후기 이미지 (service_review_images)
CREATE TABLE `service_review_images` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT,
    `service_review_id` BIGINT          NOT NULL,
    `image_url`         VARCHAR(255)    NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_service_reviews_TO_service_review_images`
        FOREIGN KEY (`service_review_id`) REFERENCES `service_reviews` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 초기 데이터 (동물 종류, 품종)
-- ============================================================

INSERT INTO `animal_types` (`name`) VALUES ('강아지');
INSERT INTO `animal_types` (`name`) VALUES ('고양이');
INSERT INTO `animal_types` (`name`) VALUES ('기타');

-- 강아지 품종
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (1, '골든 리트리버');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (1, '래브라도 리트리버');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (1, '포메라니안');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (1, '말티즈');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (1, '푸들');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (1, '시바 이누');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (1, '비숑 프리제');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (1, '웰시 코기');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (1, '치와와');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (1, '요크셔 테리어');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (1, '기타');

-- 고양이 품종
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (2, '코리안 숏헤어');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (2, '페르시안');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (2, '러시안 블루');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (2, '브리티시 숏헤어');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (2, '스코티시 폴드');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (2, '먼치킨');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (2, '랙돌');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (2, '샴');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (2, '아메리칸 숏헤어');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (2, '기타');

-- 기타 품종
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (3, '토끼');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (3, '햄스터');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (3, '앵무새');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (3, '거북이');
INSERT INTO `breeds` (`animal_type_id`, `name`) VALUES (3, '기타');

-- ============================================================
-- 초기 데이터 (미션 - 발자국 남기기 54개)
-- → application에서 missions_data.sql 로 로드 (ON DUPLICATE KEY UPDATE)
-- ============================================================

-- ============================================================
-- 초기 데이터 (자주 묻는 질문 - Figma Q&A 화면 기준)
-- ============================================================

INSERT INTO `faqs` (`question`, `answer`, `order_index`) VALUES ('이별 후에는 장례식장을 찾아볼 수 없는 건가요?', '이별 후에도 포에버 앱에서 등록된 반려동물 장례업체 목록을 계속 조회하실 수 있습니다. 다만 이별 전에 미리 장례업체를 알아보고 저장해두시면, 급한 상황에서 더 빠르게 연락하실 수 있습니다.', 1);
INSERT INTO `faqs` (`question`, `answer`, `order_index`) VALUES ('음성 녹음만 업로드 할 수 있나요?', '아니요. 포에버에서는 사진, 영상, 음성 녹음 등 다양한 형태의 추억을 남기실 수 있습니다. 발자국 남기기 미션을 통해 원하시는 방식으로 소중한 순간을 기록해보세요.', 2);
INSERT INTO `faqs` (`question`, `answer`, `order_index`) VALUES ('이별 전에 별자리 추모관을 보여주는 이유가 무엇인가요?', '이별 전에 별자리 추모관을 미리 보여드리는 것은, 반려동물과 함께할 수 있는 시간을 더 의미 있게 쓰실 수 있도록 돕기 위함입니다. 미리 알아두시면 이별 후에도 추모 공간을 편하게 이용하실 수 있습니다.', 3);
INSERT INTO `faqs` (`question`, `answer`, `order_index`) VALUES ('그 외 기타 질문 내용', '기타 문의가 있으시면 pawever01@gmail.com 로 메일을 보내주세요. 영업시간 10:00 - 19:00 (24시간 이내 답변) 안내드리겠습니다.', 4);
