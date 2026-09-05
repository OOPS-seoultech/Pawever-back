package com.pawever.backend.admin.dto;

import com.pawever.backend.admin.entity.AdminOrderView;

import java.util.List;
import java.util.Map;

/**
 * 주문 목록과 요약.
 *
 * @param summary    상태별 건수. 요구서 4-1 이 요구하는 "결제 완료·제작 중·발송 대기" 수다
 * @param viewCounts 뷰마다 몇 건인지. 탭에 숫자가 없으면 어느 일이 밀려 있는지
 *                   알려고 탭을 하나씩 눌러 봐야 한다. 필터·검색어와 무관하게
 *                   전체를 센다 — 탭은 "무엇이 남았나"이지 "지금 보는 목록이
 *                   몇 건인가"가 아니다
 */
public record AdminOrderListResponse(
        List<AdminOrderSummary> orders,
        int totalCount,
        int page,
        int size,
        Summary summary,
        Map<AdminOrderView, Long> viewCounts
) {
    public record Summary(long paymentCompleted, long inProduction, long readyToShip) {
    }
}
