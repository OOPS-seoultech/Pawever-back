-- 설문 접수와 굿즈 접수를 각각의 스위치로 나눈다.
--
-- 지금까지는 모집 기간(starts_at~ends_at)과 남은 정원 하나로 둘을 함께 판단했다.
-- 그래서 1차 무료 제작이 마감되자 설문 시작까지 막혔다. 설문은 그 자체가
-- 목적이므로 굿즈와 같은 값으로 열고 닫으면 안 된다.
--
-- 기본값을 닫힘으로 두는 이유: 값이 빠지거나 잘못 들어와도 열리는 쪽이 아니라
-- 닫히는 쪽으로 기울어야, 마감된 모집이 실수로 다시 열리지 않는다.
ALTER TABLE `goods_survey_campaigns`
    ADD COLUMN `survey_open` BOOLEAN NOT NULL DEFAULT FALSE
        AFTER `historical_allocated`;

ALTER TABLE `goods_survey_campaigns`
    ADD COLUMN `goods_open` BOOLEAN NOT NULL DEFAULT FALSE
        AFTER `survey_open`;

-- 1차 무료 제작은 마감된 상태를 유지하고, 설문만 다시 연다.
-- 굿즈는 남은 정원이 얼마든 이 스위치가 꺼져 있으면 열리지 않는다.
UPDATE `goods_survey_campaigns`
SET `survey_open` = TRUE,
    `goods_open`  = FALSE,
    `updated_at`  = CURRENT_TIMESTAMP(6)
WHERE `id` = 'goods-2026-07';
