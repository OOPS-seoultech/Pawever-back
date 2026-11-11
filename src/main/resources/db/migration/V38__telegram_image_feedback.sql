CREATE TABLE telegram_image_feedback (
 job_id varchar(36) NOT NULL PRIMARY KEY, version bigint NOT NULL DEFAULT 0,
 session_id varchar(36) NOT NULL UNIQUE, account_id bigint NOT NULL,
 telegram_user_id bigint NOT NULL, chat_id varchar(255) NOT NULL, control_message_id bigint NOT NULL,
 original_text varchar(4096) NOT NULL, directions varchar(80) NOT NULL, regions varchar(180) NOT NULL,
 note varchar(800) NOT NULL, prompt_message_id bigint, last_note_update bigint NOT NULL DEFAULT 0,
 status varchar(20) NOT NULL, submitted_job_id varchar(36), expires_at timestamp(6) NOT NULL,
 INDEX idx_feedback_reply (chat_id,prompt_message_id)
);
