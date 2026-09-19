-- 2차 기획의 "배송 완료 후 3개월"을 90일로 근사하지 않는다.
-- 아직 파기 시점이 지나지 않은 주문만 연장한다. 이미 삭제된 사진·상세주소는 복구하지 않으며,
-- 이 migration은 어떤 데이터를 즉시 삭제하지 않는다.
UPDATE goods_survey_fulfillments
SET delete_after = CONVERT_TZ(
  DATE_ADD(CONVERT_TZ(delivery_completed_at, '+00:00', '+09:00'), INTERVAL 3 MONTH),
  '+09:00', '+00:00'
)
WHERE delivery_completed_at IS NOT NULL
  AND delete_after IS NOT NULL
  AND delete_after > UTC_TIMESTAMP(6);
