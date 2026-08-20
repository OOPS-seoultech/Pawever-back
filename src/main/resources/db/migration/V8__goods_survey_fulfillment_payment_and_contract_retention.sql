-- 유료 전환에 필요한 값과, 계약 기록의 보유 기간을 담을 자리.
--
-- 이 네 컬럼은 엔티티에 먼저 들어가고 마이그레이션이 빠진 채 배포됐다.
-- 운영은 ddl-auto: none 이라 기동은 됐지만, 이 테이블을 건드리는 쿼리는
-- 컬럼이 없어 실패하는 상태였다. 굿즈가 닫혀 있어 겉으로 드러나지 않았을 뿐이다.
ALTER TABLE `goods_survey_fulfillments`
    -- 설문에 답하고 온 신청인지. 청구할 금액이 여기서 갈린다.
    -- 1차는 설문을 마쳐야만 신청할 수 있었으므로 기존 행은 모두 참이다.
    ADD COLUMN `survey_participant` BOOLEAN NOT NULL DEFAULT TRUE
        AFTER `privacy_consented_at`,
    -- 청구할 금액. 1차는 무료 체험단이라 0 이다.
    ADD COLUMN `applied_price_krw` INT NOT NULL DEFAULT 0
        AFTER `survey_participant`,
    -- 입금을 확인한 시각.
    ADD COLUMN `paid_at` DATETIME(6) NULL
        AFTER `applied_price_krw`,
    -- 계약·결제·공급 기록을 지울 때. 전자상거래법이 5년 보존을 요구한다.
    --
    -- 기존 100건은 비워 둔다. 1차는 무상 제공이었고 "배송 완료 후 90일"로
    -- 고지하고 받았다. 소급해서 5년으로 늘리면 고지한 것보다 오래 갖고 있게 된다.
    -- 비어 있으면 설문 보유 기간(2년) 파기가 그대로 정리한다.
    ADD COLUMN `contract_delete_after` DATETIME(6) NULL
        AFTER `delete_after`;

-- 파기 배치가 매일 훑는 두 기준이다.
CREATE INDEX `IDX_goods_survey_fulfillments_delete_after`
    ON `goods_survey_fulfillments` (`delete_after`);
CREATE INDEX `IDX_goods_survey_fulfillments_contract_delete_after`
    ON `goods_survey_fulfillments` (`contract_delete_after`);
