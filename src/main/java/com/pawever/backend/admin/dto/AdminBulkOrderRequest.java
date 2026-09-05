package com.pawever.backend.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 여러 주문을 한 번에 다루는 요청.
 *
 * 주문번호를 본문으로 받는다. 주소에 실으면 백 개가 줄줄이 붙어 길이 제한에
 * 걸리고, 방문 기록과 중간 프록시에 어느 주문을 만졌는지가 그대로 남는다.
 *
 * @param orderNumbers 처리할 주문번호. 건수 상한은 서비스가 본다
 */
public record AdminBulkOrderRequest(
        @NotEmpty List<@Size(max = 20) String> orderNumbers
) {
}
