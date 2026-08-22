-- 배송비를 주문에 적는다.
--
-- 화면에는 제작비와 배송비를 나눠 보여주고 결제는 한 번에 받는다. 그러려면
-- 청구액에 배송비가 들어가야 하고, 나중에 배송비가 바뀌어도 이미 받은 주문의
-- 금액은 그대로여야 하므로 설정값이 아니라 주문에 적어 둔다.
--
-- 기본값 0: 1차 체험단 100건은 무료였고 배송비도 받지 않았다. 지금 값을
-- 채워 넣으면 받지도 않은 돈을 받은 것으로 적게 된다.
ALTER TABLE `goods_survey_fulfillments`
    ADD COLUMN `shipping_fee_krw` INT NOT NULL DEFAULT 0 AFTER `promotion_name`;
