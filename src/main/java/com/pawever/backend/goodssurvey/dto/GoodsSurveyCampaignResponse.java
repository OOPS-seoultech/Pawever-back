package com.pawever.backend.goodssurvey.dto;

import java.time.Instant;

/**
 * @param open       구버전 화면 호환용. {@code goodsOpen}과 항상 같은 값이다.
 *                   랜딩은 정적 파일로 배포돼 배포 뒤에도 예전 자바스크립트가
 *                   브라우저에 남아 있는데, 그쪽은 이 값이 없으면 열린 것으로 본다.
 *                   빼는 순간 마감된 굿즈 신청 버튼이 다시 살아난다.
 * @param surveyOpen 설문 접수 여부
 * @param goodsOpen  굿즈 접수 여부. 스위치와 남은 정원을 모두 본 결과다.
 */
public record GoodsSurveyCampaignResponse(
        String campaignId,
        int capacity,
        int allocated,
        int remaining,
        Instant startsAt,
        Instant endsAt,
        boolean open,
        boolean surveyOpen,
        boolean goodsOpen
) {
}
