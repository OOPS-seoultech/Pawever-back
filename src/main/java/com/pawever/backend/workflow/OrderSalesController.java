package com.pawever.backend.workflow;

import com.pawever.backend.global.common.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OrderSalesController {
  private final OrderSalesService service;

  @GetMapping("/api/admin/workflow/sales-summary")
  public Object summary() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.summary()));
  }

  @PostMapping("/api/admin/orders/{number}/sale-price")
  public Object change(@PathVariable String number, @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.change(number, key, body));
  }
}
