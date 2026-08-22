package com.pawever.backend.payment.service;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.service.GoodsOrderService;
import com.pawever.backend.payment.client.TossPaymentsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 굿즈 주문의 결제를 확정한다.
 *
 * 화면이 성공 주소로 돌아왔다는 것만으로는 아무것도 확정하지 않는다. 그
 * 주소는 누구나 열 수 있고, 금액도 주문번호도 고쳐 부를 수 있다. 서버가
 * 토스에 직접 물어 확인한 값만 주문에 적는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsPaymentService {

    private final GoodsSurveyFulfillmentRepository fulfillmentRepository;
    private final GoodsOrderService orderService;
    private final TossPaymentsClient tossClient;
    private final Clock goodsSurveyClock;

    /**
     * 결제창에서 돌아온 결과를 확정한다.
     *
     * 승인을 부르기 전에 우리가 적어 둔 금액과 대조한다. 승인이 지나가면 돈은
     * 이미 빠져나간 뒤라, 그때 금액이 다른 것을 알아도 되돌리는 일이 남는다.
     */
    @Transactional
    public PaymentResult confirm(String orderNumber, String paymentKey, int amount) {
        GoodsSurveyFulfillment fulfillment = fulfillmentRepository
                .findByOrderNumber(orderNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));

        // 이미 끝난 결제로 다시 돌아온 경우다. 새로 승인하지 않는다.
        if (fulfillment.getPaidAt() != null) {
            return PaymentResult.of(fulfillment);
        }
        requirePayable(fulfillment);

        if (fulfillment.getPaymentAmountKrw() != amount) {
            // 화면이 보낸 금액이 우리가 적어 둔 금액과 다르다. 승인을 부르지 않는다.
            log.warn("결제 금액이 주문과 다릅니다. order={}", orderNumber);
            throw new CustomException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        var payment = tossClient.confirm(paymentKey, orderNumber, amount);
        return apply(fulfillment, payment);
    }

    /**
     * 웹훅을 받고 다시 확인한다.
     *
     * 웹훅이 실어 보낸 내용을 그대로 믿지 않는다. 이 주소는 밖에서 부를 수
     * 있으므로, 받은 값을 사실로 적으면 아무나 결제 완료를 만들 수 있다.
     * 결제 번호만 꺼내 토스에 다시 물어본다.
     *
     * 같은 웹훅이 여러 번 와도 결과는 한 번만 적힌다.
     */
    @Transactional
    public void handleWebhook(String orderNumber, String paymentKey) {
        if (orderNumber == null || paymentKey == null) {
            return;
        }
        GoodsSurveyFulfillment fulfillment = fulfillmentRepository
                .findByOrderNumber(orderNumber)
                .orElse(null);
        if (fulfillment == null || fulfillment.getPaidAt() != null) {
            // 모르는 주문이거나 이미 적어 둔 결제다. 조용히 넘어간다.
            return;
        }

        var payment = tossClient.find(paymentKey);
        if (payment == null || !payment.isDone()) {
            return;
        }
        apply(fulfillment, payment);
    }

    private PaymentResult apply(
            GoodsSurveyFulfillment fulfillment,
            TossPaymentsClient.TossPayment payment
    ) {
        if (payment == null || !payment.isDone()) {
            throw new CustomException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }
        // 토스가 실제로 승인한 금액을 한 번 더 본다. 승인 요청과 결과가 다를
        // 이유는 없지만, 다르다면 우리 기록이 아니라 토스 쪽이 사실이다.
        if (payment.totalAmount() == null
                || payment.totalAmount() != fulfillment.getPaymentAmountKrw()) {
            log.warn("승인된 금액이 주문과 다릅니다. order={}", fulfillment.getOrderNumber());
            throw new CustomException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        Instant paidAt = payment.approvedInstant() == null
                ? goodsSurveyClock.instant()
                : payment.approvedInstant();
        GoodsOrderStatus before = fulfillment.getStatus();

        // markPaid 는 이미 적힌 결제가 있으면 false 를 돌려준다. 웹훅과 승인이
        // 겹쳐 들어와도 이력이 두 번 남지 않는다.
        if (fulfillment.markPaid(paidAt, payment.paymentKey(), payment.method())) {
            orderService.recordManualChange(
                    fulfillment.getResponseId(),
                    before,
                    GoodsOrderStatus.PAYMENT_COMPLETED,
                    "toss",
                    null
            );
        }
        return PaymentResult.of(fulfillment);
    }

    private void requirePayable(GoodsSurveyFulfillment fulfillment) {
        if (fulfillment.getStatus() != GoodsOrderStatus.PAYMENT_PENDING) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_PAYABLE);
        }
        if (fulfillment.isPaymentExpired(goodsSurveyClock.instant())) {
            // 30분이 지났다. 여기서 막지 않으면 만료 처리와 결제가 엇갈린다.
            throw new CustomException(ErrorCode.PAYMENT_ALREADY_EXPIRED);
        }
    }

    /** 화면에 돌려줄 결과. 결제 수단 상세는 담지 않는다. */
    public record PaymentResult(
            String orderNumber,
            GoodsOrderStatus status,
            int paymentAmountKrw,
            Instant paidAt
    ) {

        static PaymentResult of(GoodsSurveyFulfillment fulfillment) {
            return new PaymentResult(
                    fulfillment.getOrderNumber(),
                    fulfillment.getStatus(),
                    fulfillment.getPaymentAmountKrw(),
                    fulfillment.getPaidAt()
            );
        }
    }
}
