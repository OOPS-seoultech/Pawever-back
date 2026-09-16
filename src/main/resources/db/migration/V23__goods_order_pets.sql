-- 한 주문에 아이 여러 마리.
-- 두 마리를 넣고 한 마리 값만 받던 일을 막으려면 아이를 줄로 나눠 세야 한다.

CREATE TABLE goods_order_pets (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_number VARCHAR(20) NOT NULL,
  pet_index INT NOT NULL,
  -- 주문 줄의 pet_name 과 같은 방식으로 암호화해 넣는다.
  pet_name VARCHAR(1000) NOT NULL,
  keyring_added TINYINT(1) NOT NULL DEFAULT 0,
  low_photo_acknowledged TINYINT(1) NOT NULL DEFAULT 0,
  photo_count INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  UNIQUE KEY uk_goods_order_pets_order_index (order_number, pet_index),
  KEY ix_goods_order_pets_order (order_number)
);

-- 몇 마리짜리 주문인지. 예전 주문은 모두 한 마리다.
ALTER TABLE goods_survey_fulfillments
  ADD COLUMN pet_count INT NOT NULL DEFAULT 1 AFTER pet_name;

-- 사진이 어느 아이 것인지. 예전 사진은 한 마리뿐이라 비운다.
ALTER TABLE goods_survey_photos
  ADD COLUMN pet_index INT NULL;

-- 예전 주문도 아이 한 줄씩 갖게 한다. 화면과 정산이 주문마다 다른 모양을
-- 다루지 않도록, 읽는 쪽은 언제나 아이 줄을 본다.
-- pet_name 은 같은 열 형식이라 암호문을 그대로 옮긴다.
INSERT INTO goods_order_pets
  (order_number, pet_index, pet_name, keyring_added, low_photo_acknowledged,
   photo_count, created_at, updated_at)
SELECT f.order_number,
       0,
       f.pet_name,
       f.keyring_added,
       0,
       0,
       f.created_at,
       f.updated_at
FROM goods_survey_fulfillments f
WHERE f.order_number IS NOT NULL;
