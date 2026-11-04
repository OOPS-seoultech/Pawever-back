package com.pawever.backend.workflow;

import com.pawever.backend.global.common.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AdministrativeCompletionController {
  private final AdministrativeCompletionService service;

  @PostMapping("/api/admin/orders/administrative-completions")
  public Object complete(@RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.complete(key, body));
  }

  @PostMapping("/api/admin/orders/{number}/restore-administrative-completion")
  public Object restore(@PathVariable String number, @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.restore(number, key, body));
  }
}
