-- 결제가 만료된 사람이 다시 살 수 있게 한다.
--
-- 연락처 해시에 유일 제약이 걸려 있었다. 한 캠페인에서 한 번 주문하면 그 번호는
-- 영영 다시 쓸 수 없다는 뜻이다. 결제가 만료되거나 주문이 취소돼도 마찬가지였다.
--
-- 자리는 이미 돌려주고 있다 — countSubmittedAllocations 가 만료·취소·실패를 뺀다.
-- 그런데 번호는 놓지 않아서, 돌아온 자리를 정작 그 사람만 못 쓴다. 현장에서
-- 48시간 안에 입금하지 못한 사람이 정확히 이 경우다.
--
-- 찾는 속도는 그대로 두어야 하므로 유일 제약만 걷고 일반 인덱스를 남긴다.
ALTER TABLE `goods_survey_fulfillments`
    DROP INDEX `UK_goods_survey_fulfillments_phone_hash`;

ALTER TABLE `goods_survey_fulfillments`
    ADD INDEX `IX_goods_survey_fulfillments_phone_hash` (`phone_hash`);
