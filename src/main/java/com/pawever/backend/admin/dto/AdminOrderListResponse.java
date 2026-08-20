package com.pawever.backend.admin.dto;

import java.util.List;

/**
 * 주문 목록과 요약.
 *
 * @param summary 상태별 건수. 요구서 4-1 이 요구하는 "결제 완료·제작 중·발송 대기" 수다
 */
public record AdminOrderListResponse(
        List<AdminOrderSummary> orders,
        int totalCount,
        int page,
        int size,
        Summary summary
) {
    public record Summary(long paymentCompleted, long inProduction, long readyToShip) {
    }
}
