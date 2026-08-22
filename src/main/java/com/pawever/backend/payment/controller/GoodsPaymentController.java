package com.pawever.backend.payment.controller;

import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.payment.config.TossPaymentsProperties;
import com.pawever.backend.payment.dto.GoodsPaymentConfirmRequest;
import com.pawever.backend.payment.service.GoodsPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 결제창을 열고 결과를 확정하는 통로.
 *
 * 로그인이 없다. 굿즈 신청은 회원가입 없이 받으므로, 주문번호와 결제 번호를
 * 아는 사람만 부를 수 있는 것으로 본다. 그래서 이 통로로는 아무것도 알려주지
 * 않는다 — 금액이 맞는지, 주문이 있는지는 서버가 안에서만 판단한다.
 */
@RestController
@RequestMapping("/api/public/goods-survey/payments")
@RequiredArgsConstructor
public class GoodsPaymentController {

    private final GoodsPaymentService paymentService;
    private final TossPaymentsProperties properties;

    /**
     * 화면이 결제창을 열 때 쓰는 값.
     *
     * 클라이언트 키는 밖에 나가도 되는 값이다. 시크릿 키는 여기 담지 않는다.
     */
    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        return ApiResponse.ok(Map.of(
                "clientKey", properties.getClientKey(),
                "enabled", properties.isConfigured() && !properties.getClientKey().isBlank()
        ));
    }

    /**
     * 결제창에서 돌아온 결과를 확정한다.
     *
     * 성공 주소에 도달한 것만으로는 확정하지 않는다. 서버가 토스에 직접 물어
     * 승인된 금액을 확인한 뒤에만 주문을 결제 완료로 옮긴다.
     */
    @PostMapping("/confirm")
    public ApiResponse<GoodsPaymentService.PaymentResult> confirm(
            @Valid @RequestBody GoodsPaymentConfirmRequest request
    ) {
        return ApiResponse.ok(paymentService.confirm(
                request.orderId(), request.paymentKey(), request.amount()));
    }

    /**
     * 토스가 결제 상태 변화를 알려 오는 자리.
     *
     * 실어 보낸 내용을 그대로 믿지 않는다. 이 주소는 밖에서 부를 수 있으므로,
     * 받은 값을 사실로 적으면 아무나 결제 완료를 만들 수 있다. 결제 번호만
     * 꺼내 토스에 다시 물어본다.
     *
     * 무엇이 오든 200 으로 답한다. 오류를 돌려주면 토스가 같은 웹훅을 계속
     * 다시 보내고, 그 사이에 우리 쪽 문제는 그대로 남는다.
     */
    @PostMapping("/webhook")
    public ApiResponse<Void> webhook(@RequestBody(required = false) Map<String, Object> payload) {
        if (payload != null) {
            Object data = payload.get("data");
            Map<?, ?> body = data instanceof Map<?, ?> map ? map : payload;
            paymentService.handleWebhook(
                    asText(body.get("orderId")),
                    asText(body.get("paymentKey"))
            );
        }
        return ApiResponse.ok();
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
