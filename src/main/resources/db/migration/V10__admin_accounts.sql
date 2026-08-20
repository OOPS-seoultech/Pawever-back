-- 관리자 도메인에 로그인하는 계정.
--
-- 앱 회원 테이블에 역할 칸을 더하는 편이 손은 덜 간다. 그러나 소셜 로그인으로
-- 만들어진 계정 하나가 뚫리면 고객 주소와 연락처까지 열린다. 들어오는 문이
-- 아예 달라야 한다.
--
-- 비밀번호는 만들어 주지 않는다. 초대 값의 해시만 두고 본인이 정한다.
-- 원본을 저장하면 데이터베이스를 들여다본 사람이 남의 초대 링크를 그대로
-- 만들어 계정을 가로챌 수 있다.
CREATE TABLE `admin_accounts`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `email`              VARCHAR(200) NOT NULL,
    `name`               VARCHAR(50)  NOT NULL,
    -- ADMIN 은 전체, PRODUCTION 은 제작에 필요한 것만 본다.
    `role`               VARCHAR(20)  NOT NULL,
    -- INVITED / ACTIVE / DISABLED
    `status`             VARCHAR(20)  NOT NULL,
    -- 비밀번호를 정하기 전에는 비어 있다.
    `password_hash`      VARCHAR(100) NULL,
    `invite_token_hash`  VARCHAR(88)  NULL,
    `invite_expires_at`  DATETIME(6)  NULL,
    `last_login_at`      DATETIME(6)  NULL,
    `created_at`         DATETIME(6)  NULL,
    `updated_at`         DATETIME(6)  NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_admin_accounts_email` (`email`),
    -- 초대 링크로 계정을 찾는다.
    KEY `IDX_admin_accounts_invite_token_hash` (`invite_token_hash`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
