package com.pawever.backend.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 결제창이 돌려준 값.
 *
 * 금액을 함께 받지만 그대로 쓰지 않는다. 서버가 주문에 적어 둔 금액과
 * 대조하는 데만 쓴다. 이 값을 믿으면 요청을 고쳐 1원짜리 주문을 만들 수 있다.
 *
 * @param orderId 우리 주문번호. 토스에는 orderId 라는 이름으로 넘어가 있다
 */
public record GoodsPaymentConfirmRequest(
        @NotBlank String orderId,
        @NotBlank String paymentKey,
        @Min(0) int amount
) {
}
