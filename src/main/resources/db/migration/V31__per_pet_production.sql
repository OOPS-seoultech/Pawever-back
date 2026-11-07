-- Keep one commercial order; task histories, failures and compensation belong to each pet.
ALTER TABLE production_tasks
  ADD COLUMN pet_index INT NOT NULL DEFAULT 0,
  DROP INDEX uk_order_stage_attempt,
  ADD UNIQUE KEY uk_order_pet_stage_attempt(order_number, pet_index, stage, attempt);
ALTER TABLE workflow_issues
  ADD COLUMN pet_index INT NOT NULL DEFAULT 0,
  DROP INDEX uk_order_issue,
  ADD UNIQUE KEY uk_order_pet_issue(order_number, pet_index, code);
UPDATE workflow_issues SET pet_index=-1 WHERE code IN ('PAYMENT_MISMATCH','NOTIFICATION_FAILED');
ALTER TABLE production_settlements
  ADD COLUMN pet_index INT NOT NULL DEFAULT 0,
  DROP INDEX order_number,
  ADD UNIQUE KEY uk_order_pet_settlement(order_number, pet_index);

-- Existing first-pet tasks and files stay in place. Start only missing pets of paid,
-- active, unshipped orders already enrolled in the production workflow.
INSERT INTO production_tasks(order_number, pet_index, stage, attempt, assignee_id, status)
SELECT p.order_number, p.pet_index, 'MODELING', 1,
  (SELECT t.assignee_id FROM production_tasks t WHERE t.order_number=p.order_number
    AND t.pet_index=0 AND t.stage='MODELING' ORDER BY t.id DESC LIMIT 1), 'WAITING'
FROM goods_order_pets p JOIN goods_survey_fulfillments f ON f.order_number=p.order_number
WHERE p.pet_index>0 AND f.status IN ('PAYMENT_COMPLETED','IN_PRODUCTION','LEGACY_FREE')
  AND f.lifecycle_order_status='ACTIVE' AND f.lifecycle_shipment_status='NOT_READY'
  AND f.lifecycle_payment_status IN ('CONFIRMED','NOT_REQUIRED')
  AND EXISTS (SELECT 1 FROM production_tasks t WHERE t.order_number=p.order_number)
  AND NOT EXISTS (SELECT 1 FROM production_tasks t WHERE t.order_number=p.order_number AND t.pet_index=p.pet_index);
UPDATE goods_survey_fulfillments f SET f.production_stage='MODELING_QUEUE', f.version=f.version+1
WHERE f.lifecycle_order_status='ACTIVE' AND f.lifecycle_shipment_status='NOT_READY'
  AND EXISTS (SELECT 1 FROM production_tasks t WHERE t.order_number=f.order_number
    AND t.pet_index>0 AND t.stage='MODELING' AND t.status='WAITING');
