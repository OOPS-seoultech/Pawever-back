package com.pawever.backend.workflow;

import com.pawever.backend.global.common.ApiResponse;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AsCaseController {
  private final AsCaseService service;

  @GetMapping("/api/admin/completed-orders")
  public Object completedOrders() {
    return ApiResponse.ok(service.completedOrders());
  }

  @GetMapping("/api/admin/as-cases")
  public Object cases() {
    return ApiResponse.ok(service.listCases());
  }

  @PostMapping("/api/admin/as-cases")
  public Object open(@RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.open(input));
  }

  @GetMapping("/api/admin/as-cases/{caseId}/assets")
  public Object assets(@PathVariable Long caseId) {
    return ApiResponse.ok(service.availableAssets(caseId));
  }

  @PostMapping("/api/admin/as-cases/{caseId}/access-grants")
  public Object grant(@PathVariable Long caseId, @RequestBody Map<String, Object> input) {
    return ApiResponse.ok(service.grant(caseId, input));
  }

  @PostMapping("/api/admin/as-cases/{caseId}/access-grants/{grantId}/revoke")
  public Object revoke(@PathVariable Long caseId, @PathVariable Long grantId) {
    return ApiResponse.ok(service.revoke(caseId, grantId));
  }

  @PostMapping("/api/admin/as-cases/{caseId}/close")
  public Object close(@PathVariable Long caseId) {
    return ApiResponse.ok(service.close(caseId));
  }

  @GetMapping("/api/production/as-cases/{caseId}/assets/{assetId}")
  public Object download(@PathVariable Long caseId, @PathVariable String assetId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("Referrer-Policy", "no-referrer")
        .body(ApiResponse.ok(service.download(caseId, assetId)));
  }

  @GetMapping("/api/production/as-cases")
  public Object mine() {
    return ApiResponse.ok(service.myCases());
  }
}
