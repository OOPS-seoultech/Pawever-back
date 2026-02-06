-- ============================================================
-- Pawever Database Schema (MariaDB)
-- JPA Entity 구조와 동기화된 DDL
-- hibernate ddl-auto: update 사용 시 자동 생성되지만
-- 수동 생성이 필요한 경우 이 스크립트를 사용
-- ============================================================

-- 1. 회원 (users)
CREATE TABLE `users` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT,
    `name`              VARCHAR(255)    NULL,
    `nickname`          VARCHAR(255)    NULL,
    `phone`             VARCHAR(255)    NULL,
    `kakao_id`          VARCHAR(255)    NULL,
    `naver_id`          VARCHAR(255)    NULL,
    `profile_image_url` VARCHAR(255)    NULL,
    `deleted_at`        DATETIME(6)     NULL,
    `created_at`        DATETIME(6)     NULL,
    `updated_at`        DATETIME(6)     NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 동물 종류 (animal_type)
CREATE TABLE `animal_type` (
    `id`    BIGINT          NOT NULL AUTO_INCREMENT,
    `name`  VARCHAR(255)    NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 품종 (breed)
CREATE TABLE `breed` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT,
    `animal_type_id`  BIGINT          NOT NULL,
    `name`            VARCHAR(255)    NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_animal_type_TO_breed`
        FOREIGN KEY (`animal_type_id`) REFERENCES `animal_type` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 반려동물 (pet)
CREATE TABLE `pet` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT,
    `breed_id`          BIGINT          NULL,
    `name`              VARCHAR(255)    NOT NULL,
    `birth_date`        DATE            NULL,
    `gender`            VARCHAR(10)     NULL        COMMENT 'MALE / FEMALE',
    `weight`            FLOAT           NULL,
    `invite_code`       VARCHAR(255)    NULL,
    `profile_image_url` VARCHAR(255)    NULL,
    `emergency_mode`    BOOLEAN         DEFAULT FALSE,
    `lifecycle_status`  VARCHAR(20)     NOT NULL    COMMENT 'BEFORE_FAREWELL / AFTER_FAREWELL',
    `created_at`        DATETIME(6)     NULL,
    `updated_at`        DATETIME(6)     NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_pet_invite_code` (`invite_code`),
    CONSTRAINT `FK_breed_TO_pet`
        FOREIGN KEY (`breed_id`) REFERENCES `breed` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 회원_반려동물 (user_pet)
CREATE TABLE `user_pet` (
    `id`        BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`   BIGINT      NOT NULL,
    `pet_id`    BIGINT      NOT NULL,
    `is_owner`  BOOLEAN     DEFAULT FALSE,
    `selected`  BOOLEAN     DEFAULT FALSE,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_user_TO_user_pet`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `FK_pet_TO_user_pet`
        FOREIGN KEY (`pet_id`) REFERENCES `pet` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. 추억남기기 미션 (mission)
CREATE TABLE `mission` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT,
    `name`          VARCHAR(255)    NOT NULL,
    `description`   TEXT            NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. 반려동물_미션 (pet_mission)
CREATE TABLE `pet_mission` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `pet_id`        BIGINT      NOT NULL,
    `mission_id`    BIGINT      NOT NULL,
    `completed`     BOOLEAN     DEFAULT FALSE,
    `completed_at`  DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_pet_TO_pet_mission`
        FOREIGN KEY (`pet_id`) REFERENCES `pet` (`id`),
    CONSTRAINT `FK_mission_TO_pet_mission`
        FOREIGN KEY (`mission_id`) REFERENCES `mission` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. 체크리스트 항목 (checklist_item)
CREATE TABLE `checklist_item` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT,
    `title`         VARCHAR(255)    NOT NULL,
    `description`   TEXT            NULL,
    `order_index`   INT             NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. 반려동물_체크리스트 (pet_checklist)
CREATE TABLE `pet_checklist` (
    `id`                BIGINT      NOT NULL AUTO_INCREMENT,
    `pet_id`            BIGINT      NOT NULL,
    `checklist_item_id` BIGINT      NOT NULL,
    `completed`         BOOLEAN     DEFAULT FALSE,
    `completed_at`      DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_pet_TO_pet_checklist`
        FOREIGN KEY (`pet_id`) REFERENCES `pet` (`id`),
    CONSTRAINT `FK_checklist_item_TO_pet_checklist`
        FOREIGN KEY (`checklist_item_id`) REFERENCES `checklist_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. 추모 (memorial)
CREATE TABLE `memorial` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `pet_id`        BIGINT      NOT NULL,
    `death_date`    DATETIME(6) NULL,
    `created_at`    DATETIME(6) NULL,
    `updated_at`    DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_memorial_pet_id` (`pet_id`),
    CONSTRAINT `FK_pet_TO_memorial`
        FOREIGN KEY (`pet_id`) REFERENCES `pet` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11. 댓글 (comment)
CREATE TABLE `comment` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT      NOT NULL,
    `memorial_id`   BIGINT      NOT NULL,
    `content`       TEXT        NOT NULL,
    `created_at`    DATETIME(6) NULL,
    `updated_at`    DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_user_TO_comment`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `FK_memorial_TO_comment`
        FOREIGN KEY (`memorial_id`) REFERENCES `memorial` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 12. 가이드라인 (guide)
CREATE TABLE `guide` (
    `id`    BIGINT          NOT NULL AUTO_INCREMENT,
    `name`  VARCHAR(255)    NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. 가이드 단계 (guide_step)
CREATE TABLE `guide_step` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT,
    `guide_id`      BIGINT          NOT NULL,
    `title`         VARCHAR(255)    NOT NULL,
    `description`   TEXT            NULL,
    `order_index`   INT             NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_guide_TO_guide_step`
        FOREIGN KEY (`guide_id`) REFERENCES `guide` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. 장례업체 (funeral_company)
CREATE TABLE `funeral_company` (
    `id`                      BIGINT          NOT NULL AUTO_INCREMENT,
    `name`                    VARCHAR(255)    NOT NULL,
    `location`                VARCHAR(255)    NULL,
    `introduction`            TEXT            NULL,
    `guide_text`              TEXT            NULL,
    `service_description`     TEXT            NULL,
    `full_observation`        BOOLEAN         DEFAULT FALSE,
    `available24_hours`       BOOLEAN         DEFAULT FALSE,
    `pickup_service`          BOOLEAN         DEFAULT FALSE,
    `memorial_stone`          BOOLEAN         DEFAULT FALSE,
    `private_memorial_room`   BOOLEAN         DEFAULT FALSE,
    `ossuary`                 BOOLEAN         DEFAULT FALSE,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15. 회원_장례업체 (user_funeral_company)
CREATE TABLE `user_funeral_company` (
    `id`                  BIGINT          NOT NULL AUTO_INCREMENT,
    `user_id`             BIGINT          NOT NULL,
    `funeral_company_id`  BIGINT          NOT NULL,
    `type`                VARCHAR(10)     NOT NULL    COMMENT 'SAVED / BLOCKED',
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_user_TO_user_funeral_company`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `FK_funeral_company_TO_user_funeral_company`
        FOREIGN KEY (`funeral_company_id`) REFERENCES `funeral_company` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 16. 후기 (review)
CREATE TABLE `review` (
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`             BIGINT      NOT NULL,
    `pet_id`              BIGINT      NOT NULL,
    `funeral_company_id`  BIGINT      NOT NULL,
    `rating`              INT         NOT NULL,
    `content`             TEXT        NULL,
    `created_at`          DATETIME(6) NULL,
    `updated_at`          DATETIME(6) NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_user_TO_review`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `FK_pet_TO_review`
        FOREIGN KEY (`pet_id`) REFERENCES `pet` (`id`),
    CONSTRAINT `FK_funeral_company_TO_review`
        FOREIGN KEY (`funeral_company_id`) REFERENCES `funeral_company` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 초기 데이터 (동물 종류, 품종)
-- ============================================================

INSERT INTO `animal_type` (`name`) VALUES ('강아지');
INSERT INTO `animal_type` (`name`) VALUES ('고양이');
INSERT INTO `animal_type` (`name`) VALUES ('기타');

-- 강아지 품종
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (1, '골든 리트리버');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (1, '래브라도 리트리버');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (1, '포메라니안');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (1, '말티즈');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (1, '푸들');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (1, '시바 이누');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (1, '비숑 프리제');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (1, '웰시 코기');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (1, '치와와');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (1, '요크셔 테리어');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (1, '기타');

-- 고양이 품종
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (2, '코리안 숏헤어');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (2, '페르시안');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (2, '러시안 블루');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (2, '브리티시 숏헤어');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (2, '스코티시 폴드');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (2, '먼치킨');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (2, '랙돌');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (2, '샴');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (2, '아메리칸 숏헤어');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (2, '기타');

-- 기타 품종
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (3, '토끼');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (3, '햄스터');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (3, '앵무새');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (3, '거북이');
INSERT INTO `breed` (`animal_type_id`, `name`) VALUES (3, '기타');

-- ============================================================
-- 초기 데이터 (미션 - 발자국 남기기)
-- ============================================================

INSERT INTO `mission` (`name`, `description`) VALUES ('함께 산책하기', '반려동물과 특별한 산책을 떠나보세요. 평소 가지 않았던 새로운 길을 함께 걸어보세요.');
INSERT INTO `mission` (`name`, `description`) VALUES ('좋아하는 간식 주기', '반려동물이 가장 좋아하는 간식을 준비해주세요.');
INSERT INTO `mission` (`name`, `description`) VALUES ('함께 사진 찍기', '반려동물과 함께 특별한 사진을 남겨보세요.');
INSERT INTO `mission` (`name`, `description`) VALUES ('발도장 찍기', '반려동물의 발도장을 기념으로 남겨보세요.');
INSERT INTO `mission` (`name`, `description`) VALUES ('편지 쓰기', '반려동물에게 마음을 담은 편지를 써보세요.');
INSERT INTO `mission` (`name`, `description`) VALUES ('함께 놀아주기', '반려동물이 좋아하는 놀이를 함께 해보세요.');
INSERT INTO `mission` (`name`, `description`) VALUES ('특별한 날 만들기', '반려동물을 위한 특별한 하루를 계획해보세요.');
INSERT INTO `mission` (`name`, `description`) VALUES ('영상 남기기', '반려동물의 일상 영상을 촬영해보세요.');
INSERT INTO `mission` (`name`, `description`) VALUES ('함께 낮잠 자기', '반려동물과 함께 편안한 낮잠 시간을 가져보세요.');
INSERT INTO `mission` (`name`, `description`) VALUES ('목욕시켜주기', '반려동물을 깨끗이 목욕시켜주세요.');

-- ============================================================
-- 초기 데이터 (체크리스트 - 이별준비)
-- ============================================================

INSERT INTO `checklist_item` (`title`, `description`, `order_index`) VALUES ('장례업체 알아보기', '주변 반려동물 장례업체를 미리 알아두세요.', 1);
INSERT INTO `checklist_item` (`title`, `description`, `order_index`) VALUES ('장례 방식 결정하기', '화장, 수목장, 납골 등 장례 방식을 미리 결정해두세요.', 2);
INSERT INTO `checklist_item` (`title`, `description`, `order_index`) VALUES ('추모 용품 준비하기', '메모리얼 스톤, 추모 액자 등을 준비해두세요.', 3);
INSERT INTO `checklist_item` (`title`, `description`, `order_index`) VALUES ('마지막 사진 정리하기', '반려동물과의 소중한 사진을 정리해두세요.', 4);
INSERT INTO `checklist_item` (`title`, `description`, `order_index`) VALUES ('가족과 이야기 나누기', '가족들과 반려동물의 이별에 대해 함께 이야기해보세요.', 5);
INSERT INTO `checklist_item` (`title`, `description`, `order_index`) VALUES ('수의사 상담하기', '반려동물의 상태에 대해 수의사와 상담해보세요.', 6);
INSERT INTO `checklist_item` (`title`, `description`, `order_index`) VALUES ('좋아하는 것 목록 만들기', '반려동물이 좋아했던 것들의 목록을 만들어보세요.', 7);
INSERT INTO `checklist_item` (`title`, `description`, `order_index`) VALUES ('편안한 환경 만들기', '반려동물이 편안하게 지낼 수 있는 환경을 만들어주세요.', 8);

-- ============================================================
-- 초기 데이터 (이별 가이드)
-- ============================================================

INSERT INTO `guide` (`name`) VALUES ('반려동물 사망 직후 대처 가이드');
INSERT INTO `guide` (`name`) VALUES ('장례 절차 안내');
INSERT INTO `guide` (`name`) VALUES ('펫로스 증후군 극복 가이드');

-- 사망 직후 대처 가이드 단계
INSERT INTO `guide_step` (`guide_id`, `title`, `description`, `order_index`) VALUES (1, '침착하게 상태 확인', '반려동물의 호흡과 심장박동을 확인하세요. 완전히 멈추었는지 확인합니다.', 1);
INSERT INTO `guide_step` (`guide_id`, `title`, `description`, `order_index`) VALUES (1, '시신 안치', '깨끗한 담요나 수건으로 감싸주세요. 서늘한 곳에 안치합니다.', 2);
INSERT INTO `guide_step` (`guide_id`, `title`, `description`, `order_index`) VALUES (1, '장례업체 연락', '미리 알아둔 장례업체에 연락하세요. 24시간 운영 업체가 많습니다.', 3);
INSERT INTO `guide_step` (`guide_id`, `title`, `description`, `order_index`) VALUES (1, '가족에게 알리기', '함께 반려동물을 돌봤던 가족들에게 알려주세요.', 4);

-- 장례 절차 안내 단계
INSERT INTO `guide_step` (`guide_id`, `title`, `description`, `order_index`) VALUES (2, '장례업체 방문', '예약한 장례업체를 방문합니다.', 1);
INSERT INTO `guide_step` (`guide_id`, `title`, `description`, `order_index`) VALUES (2, '추모 시간', '마지막으로 반려동물과 함께하는 시간을 가집니다.', 2);
INSERT INTO `guide_step` (`guide_id`, `title`, `description`, `order_index`) VALUES (2, '화장 또는 장례 진행', '선택한 방식으로 장례를 진행합니다.', 3);
INSERT INTO `guide_step` (`guide_id`, `title`, `description`, `order_index`) VALUES (2, '유골 수습', '화장 후 유골을 수습하고 보관 방법을 결정합니다.', 4);

-- 펫로스 증후군 극복 가이드 단계
INSERT INTO `guide_step` (`guide_id`, `title`, `description`, `order_index`) VALUES (3, '슬픔을 인정하기', '반려동물을 잃은 슬픔은 자연스러운 감정입니다. 울어도 괜찮습니다.', 1);
INSERT INTO `guide_step` (`guide_id`, `title`, `description`, `order_index`) VALUES (3, '추억 정리하기', '함께했던 소중한 추억들을 사진이나 글로 정리해보세요.', 2);
INSERT INTO `guide_step` (`guide_id`, `title`, `description`, `order_index`) VALUES (3, '주변에 도움 구하기', '같은 경험을 한 사람들과 이야기를 나눠보세요.', 3);
INSERT INTO `guide_step` (`guide_id`, `title`, `description`, `order_index`) VALUES (3, '일상 회복하기', '천천히 일상으로 돌아가세요. 시간이 필요합니다.', 4);
