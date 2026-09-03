-- 만든 물건을 건네는 방법을 주문에 적는다.
--
-- 플리마켓은 현장에서 직접 건넨다. 받는 사람이 그 자리에 오므로 주소를 물을
-- 이유가 없고, 부치지 않으니 배송비도 없다. 지금까지는 부치는 경우만 있어서
-- 주소가 필수였고 배송비도 무조건 붙었다.
--
-- 기본값 SHIPPING: 이미 받은 주문은 모두 택배 건이다. 값이 빠지거나 잘못
-- 들어와도 부치는 쪽으로 기울어야, 부쳐야 할 물건을 안 부치는 일이 없다.
ALTER TABLE `goods_survey_fulfillments`
    ADD COLUMN `delivery_method` VARCHAR(20) NOT NULL DEFAULT 'SHIPPING'
        AFTER `phone_hash`;

-- 현장 수령은 주소를 받지 않는다. 빈 문자열을 넣어 채운 척하면 관리자 화면에
-- 주소가 있는 것처럼 보이고, 부칠 수 없는 건이 배송 대기로 섞인다.
ALTER TABLE `goods_survey_fulfillments`
    MODIFY COLUMN `postal_code` VARCHAR(1000) NULL;

ALTER TABLE `goods_survey_fulfillments`
    MODIFY COLUMN `address` VARCHAR(2000) NULL;
