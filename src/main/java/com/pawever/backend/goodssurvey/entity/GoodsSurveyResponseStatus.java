package com.pawever.backend.goodssurvey.entity;

public enum GoodsSurveyResponseStatus {
    DRAFT,
    RESERVED,
    COMPLETED_NO_SLOT,
    SUBMITTED,
    TERMINATED,
    /**
     * 설문을 건너뛰고 바로 굿즈를 신청하러 온 상태.
     *
     * RESERVED 와 나눠 둔다. 둘 다 신청까지 갈 수 있지만 내는 값이 다르다.
     * 설문에 답한 사람은 할인가, 건너뛴 사람은 정가다. 제출 시점에 이 값을 보고
     * 적용가를 정하므로 하나로 합치면 얼마를 청구할지 알 수 없게 된다.
     */
    DIRECT
}
