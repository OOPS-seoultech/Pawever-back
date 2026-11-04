package com.pawever.backend.workflow;

import static com.pawever.backend.admin.entity.PermissionKey.*;

import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class OrderSalesService {
  private final WorkflowService workflow;
  private final StaffPermissions access;
  private final GoodsSurveyFulfillmentRepository orders;
  private final Clock clock;

  private void canView() {
    access.require(VIEW_ORDER_BASIC);
    access.require(VIEW_ALL_ORDERS);
    access.require(VIEW_PAYMENT);
  }

  public Map<String, Object> change(String number, String key, Map<String, Object> body) {
    canView();
    access.require(MANAGE_OPERATION_SETTINGS);
    return workflow.command("sale-price:" + number, key, body, () -> {
      canView();
      access.require(MANAGE_OPERATION_SETTINGS);
      if (!(body.get("custom") instanceof Boolean custom))
        throw invalid();
      Integer price = null;
      if (custom) {
        if (!(body.get("salePriceKrw") instanceof Number value)) throw invalid();
        try { price = new BigDecimal(value.toString()).intValueExact(); }
        catch (ArithmeticException | NumberFormatException e) { throw invalid(); }
        if (price < 0 || price > 100_000_000) throw invalid();
      }
      var order = workflow.locked(number);
      workflow.version(order, body);
      String before = String.valueOf(order.getSalePriceKrw());
      order.changeSalePrice(price);
      workflow.touch(order);
      workflow.audit(number, "SALE_PRICE_CHANGE", before, String.valueOf(price), "관리용 판매가 변경");
      return workflow.view(order);
    });
  }

  private WorkflowException invalid() {
    return new WorkflowException(400, "INVALID_SALE_PRICE", "판매가는 0~100,000,000원 사이의 정수로 입력해 주세요.");
  }

  @Transactional(readOnly = true)
  public Map<String, Object> summary() {
    canView();
    var month = YearMonth.from(clock.instant().atZone(ZoneId.of("Asia/Seoul")));
    var totals = new TreeMap<YearMonth, Long>();
    for (int i = 11; i >= 0; i--) totals.put(month.minusMonths(i), 0L);
    long total = 0, count = 0;
    var included = EnumSet.of(GoodsOrderStatus.PAYMENT_COMPLETED, GoodsOrderStatus.IN_PRODUCTION,
        GoodsOrderStatus.SHIPPED, GoodsOrderStatus.PICKED_UP, GoodsOrderStatus.ADMIN_COMPLETED);
    for (var entry : orders.findSaleEntries()) {
      if (!included.contains(entry.getStatus())
          || (entry.getLifecyclePaymentStatus() != null && !entry.getLifecyclePaymentStatus().equals("CONFIRMED"))
          || Set.of("CANCELED", "EXPIRED", "TRASHED").contains(Objects.toString(entry.getLifecycleOrderStatus(), ""))) continue;
      long price = entry.getSalePriceKrw() == null ? 18_900L : entry.getSalePriceKrw();
      var paidMonth = YearMonth.from(entry.getPaidAt().atZone(ZoneId.of("Asia/Seoul")));
      total += price;
      count++;
      if (totals.containsKey(paidMonth)) totals.merge(paidMonth, price, Long::sum);
    }
    return Map.of("totalKrw", total, "monthKrw", totals.get(month), "paidOrderCount", count,
        "currentMonth", month.toString(), "timezone", "Asia/Seoul",
        "months", totals.entrySet().stream().map(e -> Map.of("month", e.getKey().toString(), "amountKrw", e.getValue())).toList());
  }
}
