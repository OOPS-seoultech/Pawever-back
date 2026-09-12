package com.pawever.backend.workflow;

import static com.pawever.backend.admin.entity.PermissionKey.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pawever.backend.admin.controller.AdminOrderController;
import com.pawever.backend.admin.dto.AdminOrderDetail;
import com.pawever.backend.admin.dto.AdminOrderListResponse;
import com.pawever.backend.global.common.ApiResponse;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Apply the same field permissions to the existing order and shipping screens. */
@RestControllerAdvice
@RequiredArgsConstructor
public class LegacyOrderResponseFilter implements ResponseBodyAdvice<Object> {
  private final StaffPermissions access;
  private final WorkflowService workflow;
  private final ObjectMapper mapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Override
  public boolean supports(
      MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) {
    return method.getContainingClass() == AdminOrderController.class;
  }

  private Map<String, Object> object(Object value) {
    return mapper.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter method,
      MediaType type,
      Class<? extends HttpMessageConverter<?>> converter,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    response.getHeaders().setCacheControl("no-store");
    if (!(body instanceof ApiResponse<?> envelope) || !envelope.isSuccess()) return body;
    var p = access.effective(access.current());
    if (envelope.getData() instanceof AdminOrderDetail detail) {
      var result = object(detail);
      if (!p.contains(VIEW_PAYMENT)) {
        result.put("payment", null);
        result.put("pricing", null);
      }
      if (!p.contains(VIEW_CUSTOMER_PHOTOS)) result.put("photos", List.of());
      if (detail.shipping() != null) {
        var shipping = object(detail.shipping());
        if (!p.contains(VIEW_CUSTOMER_IDENTITY)) shipping.put("guardianName", null);
        if (!p.contains(VIEW_CUSTOMER_CONTACT)) shipping.put("phone", null);
        if (!p.contains(VIEW_CUSTOMER_ADDRESS))
          for (String field : List.of("postalCode", "address", "addressDetail"))
            shipping.put(field, null);
        if (!p.contains(VIEW_SHIPMENT)) {
          shipping.put("trackingCompany", null);
          shipping.put("trackingNumber", null);
        }
        result.put("shipping", shipping);
      }
      if (!p.contains(VIEW_AUDIT_LOG)
          || !p.containsAll(
              Set.of(
                  VIEW_CUSTOMER_IDENTITY,
                  VIEW_CUSTOMER_CONTACT,
                  VIEW_CUSTOMER_ADDRESS,
                  VIEW_PAYMENT))) {
        result.put("statusHistory", List.of());
        result.put("accessLogs", List.of());
      }
      result.put("workflow", workflow.detail(detail.orderNumber()));
      return ApiResponse.ok(result);
    }
    if (envelope.getData() instanceof AdminOrderListResponse list) {
      var result = object(list);
      result.put(
          "orders",
          list.orders().stream()
              .map(
                  row -> {
                    var item = object(row);
                    if (!p.contains(VIEW_CUSTOMER_IDENTITY)) item.put("guardianNameMasked", null);
                    if (!p.contains(VIEW_CUSTOMER_CONTACT)) item.put("phoneMasked", null);
                    if (!p.contains(VIEW_PAYMENT)) {
                      item.put("paymentAmountKrw", null);
                      item.put("paidAt", null);
                    }
                    if (!p.contains(VIEW_SHIPMENT)) item.put("trackingNumber", null);
                    item.put("workflow", workflow.detail(row.orderNumber()));
                    return item;
                  })
              .toList());
      return ApiResponse.ok(result);
    }
    return body;
  }
}
