-- Existing V17 modeling/review tasks and staff assignments remain unchanged.
CREATE TABLE model_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(20) NOT NULL,
    review_task_id BIGINT NOT NULL,
    modeling_task_id BIGINT NOT NULL,
    modeling_attempt INT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    decision VARCHAR(30) NOT NULL,
    reason_code VARCHAR(30) NULL,
    note VARCHAR(300) NOT NULL,
    approved_checks VARCHAR(100) NULL,
    reviewed_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_model_review_task(review_task_id),
    KEY idx_model_review_order(order_number,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
