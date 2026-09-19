package com.pawever.backend.workflow.event;

/** 모델링 필수 자료가 모두 제출되어 검수 대기 단계로 넘어간 뒤에만 발행한다. */
public record ModelReviewRequestedEvent(String orderNumber, Long reviewTaskId) {}
