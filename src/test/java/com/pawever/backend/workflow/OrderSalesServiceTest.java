package com.pawever.backend.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class OrderSalesServiceTest {
  @Test
  void sumsConfirmedSalesAtKoreanMonthBoundaryAndExcludesFreeRefundedAndCanceled() {
    var orders = mock(GoodsSurveyFulfillmentRepository.class);
    when(orders.findSaleEntries()).thenReturn(List.of(
        entry("2026-10-31T15:00:00Z", 24000, GoodsOrderStatus.ADMIN_COMPLETED, "CONFIRMED", "COMPLETED"),
        entry("2026-10-31T14:59:59Z", null, GoodsOrderStatus.PAYMENT_COMPLETED, null, null),
        entry("2025-01-01T00:00:00Z", 10000, GoodsOrderStatus.SHIPPED, "CONFIRMED", "ACTIVE"),
        entry("2026-11-01T00:00:00Z", null, GoodsOrderStatus.ADMIN_COMPLETED, "NOT_REQUIRED", "COMPLETED"),
        entry("2026-11-01T00:00:00Z", null, GoodsOrderStatus.CANCELED, "REFUNDED", "CANCELED"),
        entry("2026-11-01T00:00:00Z", null, GoodsOrderStatus.IN_PRODUCTION, "REFUND_PENDING", "ACTIVE"),
        entry("2026-11-01T00:00:00Z", null, GoodsOrderStatus.IN_PRODUCTION, "CONFIRMED", "TRASHED")
    ));
    var service = new OrderSalesService(mock(WorkflowService.class), mock(StaffPermissions.class), orders,
        Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC));
    var result = service.summary();
    assertThat(result.get("totalKrw")).isEqualTo(52900L);
    assertThat(result.get("monthKrw")).isEqualTo(24000L);
    assertThat(result.get("paidOrderCount")).isEqualTo(3L);
    var months = (List<Map<String, Object>>) result.get("months");
    assertThat(months).hasSize(12);
    assertThat(months.get(10)).containsEntry("month", "2026-10").containsEntry("amountKrw", 18900L);
    assertThat(months.get(11)).containsEntry("month", "2026-11").containsEntry("amountKrw", 24000L);
  }

  private GoodsSurveyFulfillmentRepository.SaleEntry entry(String at, Integer price,
      GoodsOrderStatus status, String payment, String order) {
    var value = mock(GoodsSurveyFulfillmentRepository.SaleEntry.class);
    when(value.getPaidAt()).thenReturn(Instant.parse(at));
    when(value.getSalePriceKrw()).thenReturn(price);
    when(value.getStatus()).thenReturn(status);
    when(value.getLifecyclePaymentStatus()).thenReturn(payment);
    when(value.getLifecycleOrderStatus()).thenReturn(order);
    return value;
  }
}
