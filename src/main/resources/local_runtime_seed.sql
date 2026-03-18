-- ============================================================
-- Local runtime seed
-- 목적:
-- - 프론트 auth/onboarding/invite/funeral 플로우를 local DB 기준으로 바로 재현
-- - owner 1:1 펫 10마리 + guest 연결 + 만료 초대코드 + 장례업체 데이터와 함께 사용
-- 주의:
-- - users.name / phone / email은 암호화 컨버터가 있어 local SQL에서는 null로 유지
-- - invite_code는 SQL insert 시 직접 고정값을 넣는다
-- ============================================================

-- 현재 유효한 초대코드
-- 9101 보리   BORI1001
-- 9102 코코   COCO1002
-- 9103 뭉치   MUNG1003
-- 9104 루나   LUNA1004
-- 9105 초코   CHOK1005
-- 9106 모모   MOMO1006
-- 9107 대박   DAEB1007
-- 9108 나비   NABI1008
-- 9109 또치   TTOC1009
-- 9110 사랑   SARA1010
--
-- 만료된 초대코드
-- 9102 OLDC1002
-- 9104 EXPD1004
-- 9107 PAST1007

INSERT INTO pets (
    id,
    breed_id,
    name,
    birth_date,
    gender,
    weight,
    invite_code,
    profile_image_url,
    emergency_mode,
    lifecycle_status,
    death_date,
    created_at,
    updated_at
) VALUES
    (9101, 61,  '보리', '2021-05-12', 'MALE',   4.1, 'BORI1001', NULL, FALSE, 'BEFORE_FAREWELL', NULL,                    '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9102, 286, '코코', '2023-09-01', 'FEMALE', 3.4, 'COCO1002', NULL, FALSE, 'BEFORE_FAREWELL', NULL,                    '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9103, 192, '뭉치', '2018-02-17', 'MALE',  11.2, 'MUNG1003', NULL, TRUE,  'AFTER_FAREWELL',  '2026-03-01 07:30:00', '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9104, 319, '루나', '2017-11-08', 'FEMALE', 4.5, 'LUNA1004', NULL, FALSE, 'AFTER_FAREWELL',  '2025-12-11 18:45:00', '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9105, 107, '초코', '2020-07-23', 'MALE',   5.0, 'CHOK1005', NULL, FALSE, 'BEFORE_FAREWELL', NULL,                    '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9106, 346, '모모', '2022-01-14', 'FEMALE', 3.8, 'MOMO1006', NULL, TRUE,  'AFTER_FAREWELL',  '2026-02-22 11:00:00', '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9107, 279, '대박', '2019-04-30', 'MALE',   2.9, 'DAEB1007', NULL, FALSE, 'AFTER_FAREWELL',  '2024-08-19 09:10:00', '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9108, 381, '나비', '2024-01-05', 'FEMALE', 4.0, 'NABI1008', NULL, FALSE, 'BEFORE_FAREWELL', NULL,                    '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9109, 480, '또치', '2023-03-15', 'MALE',   0.6, 'TTOC1009', NULL, FALSE, 'AFTER_FAREWELL',  '2023-06-01 16:20:00', '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9110, 78,  '사랑', '2016-10-04', 'FEMALE', 18.5,'SARA1010', NULL, FALSE, 'BEFORE_FAREWELL', NULL,                    '2026-03-18 09:00:00', '2026-03-18 09:00:00')
ON DUPLICATE KEY UPDATE
    breed_id = VALUES(breed_id),
    name = VALUES(name),
    birth_date = VALUES(birth_date),
    gender = VALUES(gender),
    weight = VALUES(weight),
    invite_code = VALUES(invite_code),
    profile_image_url = VALUES(profile_image_url),
    emergency_mode = VALUES(emergency_mode),
    lifecycle_status = VALUES(lifecycle_status),
    death_date = VALUES(death_date),
    updated_at = VALUES(updated_at);

INSERT INTO users (
    id,
    name,
    nickname,
    phone,
    phone_hash,
    email,
    gender,
    birthday,
    birth_year,
    age_range,
    kakao_id,
    naver_id,
    profile_image_url,
    selected_pet_id,
    onboarding_complete,
    referral_type,
    referral_memo,
    deleted_at,
    notification_agreed_at,
    marketing_agreed_at,
    created_at,
    updated_at
) VALUES
    (9001, NULL, '보리엄마',   NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'seed_kakao_9001', NULL, NULL, 9101, TRUE,  'FRIEND',    NULL,       NULL, '2026-03-18 09:00:00', NULL,                    '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9002, NULL, '코코아빠',   NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'seed_kakao_9002', NULL, NULL, 9102, TRUE,  'INSTAGRAM', NULL,       NULL, '2026-03-18 09:00:00', '2026-03-18 09:00:00', '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9003, NULL, '뭉치누나',   NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'seed_kakao_9003', NULL, NULL, 9104, TRUE,  'OTHER',     '동네 추천', NULL, NULL,                    NULL,                    '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9004, NULL, '루나집사',   NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'seed_kakao_9004', NULL, NULL, 9104, TRUE,  'OFFLINE',   NULL,       NULL, '2026-03-18 09:00:00', NULL,                    '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9005, NULL, '초코형아',   NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'seed_kakao_9005', NULL, NULL, 9106, TRUE,  'THREADS',   NULL,       NULL, NULL,                  NULL,                    '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9006, NULL, '모모집사',   NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'seed_kakao_9006', NULL, NULL, 9106, TRUE,  'INSTAGRAM', NULL,       NULL, '2026-03-18 09:00:00', '2026-03-18 09:00:00', '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9007, NULL, '대박아빠',   NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'seed_kakao_9007', NULL, NULL, 9107, TRUE,  'OFFLINE',   NULL,       NULL, NULL,                  NULL,                    '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9008, NULL, '나비보호자', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'seed_kakao_9008', NULL, NULL, NULL, TRUE,  'OFFLINE',   NULL,       NULL, '2026-03-18 09:00:00', NULL,                    '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9009, NULL, '또치메이트', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'seed_kakao_9009', NULL, NULL, 9109, FALSE, 'INSTAGRAM', NULL,       NULL, NULL,                  NULL,                    '2026-03-18 09:00:00', '2026-03-18 09:00:00'),
    (9010, NULL, '사랑엄마',   NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'seed_kakao_9010', NULL, NULL, 9103, FALSE, 'OFFLINE',  NULL,       NULL, '2026-03-18 09:00:00', NULL,                    '2026-03-18 09:00:00', '2026-03-18 09:00:00')
ON DUPLICATE KEY UPDATE
    nickname = VALUES(nickname),
    kakao_id = VALUES(kakao_id),
    naver_id = VALUES(naver_id),
    profile_image_url = VALUES(profile_image_url),
    selected_pet_id = VALUES(selected_pet_id),
    onboarding_complete = VALUES(onboarding_complete),
    referral_type = VALUES(referral_type),
    referral_memo = VALUES(referral_memo),
    deleted_at = VALUES(deleted_at),
    notification_agreed_at = VALUES(notification_agreed_at),
    marketing_agreed_at = VALUES(marketing_agreed_at),
    updated_at = VALUES(updated_at);

INSERT INTO user_pets (
    id,
    user_id,
    pet_id,
    is_owner
) VALUES
    (9201, 9001, 9101, TRUE),
    (9202, 9002, 9102, TRUE),
    (9203, 9003, 9103, TRUE),
    (9204, 9004, 9104, TRUE),
    (9205, 9005, 9105, TRUE),
    (9206, 9006, 9106, TRUE),
    (9207, 9007, 9107, TRUE),
    (9208, 9008, 9108, TRUE),
    (9209, 9009, 9109, TRUE),
    (9210, 9010, 9110, TRUE),
    (9211, 9001, 9102, FALSE),
    (9212, 9001, 9104, FALSE),
    (9213, 9002, 9101, FALSE),
    (9214, 9003, 9104, FALSE),
    (9215, 9003, 9105, FALSE),
    (9216, 9005, 9106, FALSE),
    (9217, 9006, 9108, FALSE),
    (9218, 9008, 9101, FALSE),
    (9219, 9010, 9103, FALSE),
    (9220, 9010, 9107, FALSE),
    (9221, 9004, 9102, FALSE)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    pet_id = VALUES(pet_id),
    is_owner = VALUES(is_owner);

INSERT INTO pet_expired_invite_codes (
    id,
    pet_id,
    invite_code
) VALUES
    (9301, 9102, 'OLDC1002'),
    (9302, 9104, 'EXPD1004'),
    (9303, 9107, 'PAST1007')
ON DUPLICATE KEY UPDATE
    pet_id = VALUES(pet_id),
    invite_code = VALUES(invite_code);
