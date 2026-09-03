-- 굿즈를 파는 통로를 모집에 붙인다.
--
-- 과기대 플리마켓은 현장 한정가 11,900원에 70자리로, 상시 온라인 판매(29,900원
-- 100자리)와 값도 정원도 다르다. 지금까지는 열려 있는 모집이 하나뿐이라 설정
-- 하나로 충분했지만, 두 통로가 동시에 열리면 어느 쪽으로 들어온 주문인지를
-- 주문 자체가 알고 있어야 한다.
--
-- 통로를 설정이 아니라 여기에 두는 이유: 행사가 끝나 설정을 지워도, 이미 그
-- 모집에 붙은 주문은 사람이 동의한 값 그대로 계산돼야 한다. 설정만 보고
-- 판단하면 닫는 순간 대기 중인 주문에 29,900원이 매겨진다.
ALTER TABLE `goods_survey_campaigns`
    ADD COLUMN `channel` VARCHAR(20) NOT NULL DEFAULT 'ONLINE'
        AFTER `id`;

-- 플리마켓 모집.
--
-- 정원 70은 디자인이 화면에 적어 둔 수량이다("이번에도 선착순 70개만
-- 제작합니다"). 온라인 100자리와는 따로 센다.
--
-- goods_open 은 닫아 둔다. 행사 시작에 맞춰 사람이 켠다. 기간(starts_at~
-- ends_at)은 안내용 기록일 뿐 접수를 막지 않으므로, 끝낼 때도 날짜가 아니라
-- 이 스위치를 내려야 한다.
--
-- survey_open 도 닫아 둔다. 플리마켓은 QR 을 찍고 바로 주문하는 자리라
-- 설문을 거치지 않는다.
INSERT INTO `goods_survey_campaigns`
    (`id`, `channel`, `capacity`, `historical_allocated`,
     `survey_open`, `goods_open`, `starts_at`, `ends_at`,
     `created_at`, `updated_at`)
VALUES ('goods-2026-09-flea', 'FLEA', 70, 0,
        FALSE, FALSE, '2026-09-01 00:00:00.000000', '2026-12-31 23:59:59.000000',
        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE `updated_at` = `updated_at`;
