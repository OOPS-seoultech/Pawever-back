-- 남은 자리를 실제 신청 건수 그대로 보여준다.
--
-- V2에서 historical_allocated를 27로 넣어 두어, 신청이 하나도 없어도
-- 랜딩에 "29명 신청 완료 / 남은 자리 71"처럼 사실과 다른 숫자가 나갔다.
-- 근거 없는 숫자로 선착순을 압박하는 표시라 표시광고법상 위험이 있고,
-- 실제로도 73건에서 마감돼 모집 목표 100명을 채울 수 없다.
UPDATE `goods_survey_campaigns`
SET `historical_allocated` = 0,
    `updated_at`           = CURRENT_TIMESTAMP(6)
WHERE `id` = 'goods-2026-07';
