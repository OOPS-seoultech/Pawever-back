CREATE TABLE image_generation_jobs (
 id varchar(36) NOT NULL PRIMARY KEY, version bigint NOT NULL DEFAULT 0,
 order_number varchar(20) NOT NULL, pet_index int NOT NULL, task_id bigint NOT NULL,
 requested_by bigint NOT NULL, request_key varchar(160) NOT NULL UNIQUE,
 previous_id varchar(36), status varchar(30) NOT NULL, prompt_version varchar(80) NOT NULL,
 feedback varchar(2000), error_code varchar(200), sheet_key varchar(500), artifact_ids varchar(180),
 usage_json LONGTEXT, request_id varchar(150), created_at timestamp(6) NOT NULL,
 started_at timestamp(6), finished_at timestamp(6), reviewed_by bigint, reviewed_at timestamp(6),
 telegram_status varchar(30), control_message_id bigint,
 INDEX idx_image_job_queue (status,created_at), INDEX idx_image_job_pet (order_number,pet_index,created_at)
);
CREATE TABLE staff_telegram_links (
 account_id bigint NOT NULL PRIMARY KEY, version bigint NOT NULL DEFAULT 0,
 telegram_user_id bigint UNIQUE, code_hash varchar(64) UNIQUE, expires_at timestamp(6)
);
