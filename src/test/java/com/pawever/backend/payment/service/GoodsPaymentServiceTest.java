package com.pawever.backend.payment.service;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.goodssurvey.entity.GoodsOrderPricing;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.service.GoodsOrderService;
import com.pawever.backend.payment.client.TossPaymentsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제가 확정되는 조건을 고정한다.
 *
 * 이 통로는 로그인이 없다. 주소도 요청 내용도 밖에서 고쳐 부를 수 있다.
 * 그래서 화면이 보낸 값 가운데 무엇도 그대로 믿지 않는지를 본다.
 */
@ExtendWith(MockitoExtension.class)
class GoodsPaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T09:00:00Z");
    private static final String ORDER = "PE-2026-000101";
    private static final int AMOUNT = 26_900;

    @Mock private GoodsSurveyFulfillmentRepository fulfillmentRepository;
    @Mock private GoodsOrderService orderService;
    @Mock private TossPaymentsClient tossClient;

    private GoodsPaymentService service;

    @BeforeEach
    void setUp() {
        service = new GoodsPaymentService(
                fulfillmentRepository,
                orderService,
                tossClient,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void 금액이_다르면_승인을_부르지도_않는다() {
        // 승인이 지나가면 돈은 이미 빠져나간 뒤다. 그때 금액이 다른 것을 알아도
        // 되돌리는 일이 남는다. 부르기 전에 막는다.
        when(fulfillmentRepository.findByOrderNumber(ORDER))
                .thenReturn(Optional.of(pendingOrder()));

        assertThatThrownBy(() -> service.confirm(ORDER, "pay-1", 100))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        verify(tossClient, never()).confirm(anyString(), anyString(), anyInt());
    }

    @Test
    void 결제_시간이_지난_주문은_승인하지_않는다() {
        // 만료 처리가 아직 안 돌았어도 여기서 막는다. 안 그러면 만료된 주문에
        // 돈이 들어오고, 뒤늦게 도는 만료 작업과 엇갈린다.
        GoodsSurveyFulfillment expired = order(GoodsOrderStatus.PAYMENT_PENDING, -1);
        when(fulfillmentRepository.findByOrderNumber(ORDER)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.confirm(ORDER, "pay-1", AMOUNT))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_ALREADY_EXPIRED);

        verify(tossClient, never()).confirm(anyString(), anyString(), anyInt());
    }

    @Test
    void 토스가_승인하지_않았으면_결제_완료로_적지_않는다() {
        // 화면이 성공 주소로 돌아왔다는 것만으로는 아무것도 확정되지 않는다.
        when(fulfillmentRepository.findByOrderNumber(ORDER))
                .thenReturn(Optional.of(pendingOrder()));
        when(tossClient.confirm(anyString(), anyString(), anyInt()))
                .thenReturn(payment("ABORTED", AMOUNT));

        assertThatThrownBy(() -> service.confirm(ORDER, "pay-1", AMOUNT))
                .isInstanceOf(CustomException.class);

        verify(orderService, never())
                .recordManualChange(anyString(), any(), any(), anyString(), any());
    }

    @Test
    void 토스가_승인한_금액이_주문과_다르면_받지_않는다() {
        // 승인 요청과 결과가 다를 이유는 없지만, 다르다면 우리 기록이 아니라
        // 토스 쪽이 사실이다. 조용히 넘어가면 장부가 어긋난다.
        when(fulfillmentRepository.findByOrderNumber(ORDER))
                .thenReturn(Optional.of(pendingOrder()));
        when(tossClient.confirm(anyString(), anyString(), anyInt()))
                .thenReturn(payment("DONE", 100));

        assertThatThrownBy(() -> service.confirm(ORDER, "pay-1", AMOUNT))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    @Test
    void 승인되면_결제_완료로_옮기고_이력을_남긴다() {
        GoodsSurveyFulfillment order = pendingOrder();
        when(fulfillmentRepository.findByOrderNumber(ORDER)).thenReturn(Optional.of(order));
        when(tossClient.confirm(anyString(), anyString(), anyInt()))
                .thenReturn(payment("DONE", AMOUNT));

        var result = service.confirm(ORDER, "pay-1", AMOUNT);

        assertThat(order.getStatus()).isEqualTo(GoodsOrderStatus.PAYMENT_COMPLETED);
        assertThat(order.getPaymentKey()).isEqualTo("pay-1");
        assertThat(result.paidAt()).isNotNull();
        verify(orderService).recordManualChange(
                anyString(),
                any(GoodsOrderStatus.class),
                any(GoodsOrderStatus.class),
                anyString(),
                any()
        );
    }

    @Test
    void 같은_결제로_두_번_돌아와도_한_번만_적는다() {
        // 새로고침이나 뒤로가기로 성공 주소를 다시 열면 여기로 또 온다.
        GoodsSurveyFulfillment order = pendingOrder();
        when(fulfillmentRepository.findByOrderNumber(ORDER)).thenReturn(Optional.of(order));
        when(tossClient.confirm(anyString(), anyString(), anyInt()))
                .thenReturn(payment("DONE", AMOUNT));

        service.confirm(ORDER, "pay-1", AMOUNT);
        service.confirm(ORDER, "pay-1", AMOUNT);

        verify(tossClient).confirm(anyString(), anyString(), anyInt());
        verify(orderService).recordManualChange(
                anyString(), any(), any(), anyString(), any());
    }

    @Test
    void 웹훅이_실어_보낸_내용은_믿지_않고_다시_물어본다() {
        // 이 주소는 밖에서 부를 수 있다. 받은 값을 사실로 적으면 아무나
        // 결제 완료를 만들 수 있다.
        GoodsSurveyFulfillment order = pendingOrder();
        when(fulfillmentRepository.findByOrderNumber(ORDER)).thenReturn(Optional.of(order));
        when(tossClient.find("pay-1")).thenReturn(payment("DONE", AMOUNT));

        service.handleWebhook(ORDER, "pay-1");

        verify(tossClient).find("pay-1");
        assertThat(order.getStatus()).isEqualTo(GoodsOrderStatus.PAYMENT_COMPLETED);
    }

    @Test
    void 웹훅이_여러_번_와도_이력은_한_번만_남는다() {
        GoodsSurveyFulfillment order = pendingOrder();
        when(fulfillmentRepository.findByOrderNumber(ORDER)).thenReturn(Optional.of(order));
        when(tossClient.find("pay-1")).thenReturn(payment("DONE", AMOUNT));

        service.handleWebhook(ORDER, "pay-1");
        service.handleWebhook(ORDER, "pay-1");
        service.handleWebhook(ORDER, "pay-1");

        verify(tossClient).find("pay-1");
        verify(orderService).recordManualChange(
                anyString(), any(), any(), anyString(), any());
    }

    @Test
    void 모르는_주문번호가_와도_터지지_않는다() {
        // 웹훅에서 예외가 올라가면 토스가 같은 웹훅을 계속 다시 보낸다.
        when(fulfillmentRepository.findByOrderNumber("PE-9999-999999"))
                .thenReturn(Optional.empty());

        service.handleWebhook("PE-9999-999999", "pay-1");

        verify(tossClient, never()).find(anyString());
    }

    private GoodsSurveyFulfillment pendingOrder() {
        return order(GoodsOrderStatus.PAYMENT_PENDING, 30);
    }

    /** @param expiresInMinutes 음수면 이미 지난 주문 */
    private GoodsSurveyFulfillment order(GoodsOrderStatus status, int expiresInMinutes) {
        GoodsSurveyFulfillment fulfillment = GoodsSurveyFulfillment.create(
                "resp-1", "idem-1", "conv-1", "{}", "figure", null,
                "몽이", "김포에버", "01012345678", "phone-hash",
                "01234", "서울특별시 노원구 공릉로 232", "101호",
                "2026-07-23", NOW, true, ORDER,
                GoodsOrderPricing.discounted(29_900, 6_000, "설문 참여 할인", 3_000),
                false, "marketing-v1", expiresInMinutes, 1825
        );
        fulfillment.changeStatus(status);
        return fulfillment;
    }

    private TossPaymentsClient.TossPayment payment(String status, int amount) {
        return new TossPaymentsClient.TossPayment(
                "pay-1", ORDER, status, amount, "간편결제", "2026-08-22T18:00:00+09:00");
    }
}
