package com.pawever.backend.workflow;

import com.pawever.backend.global.common.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ProductionFinishingController {
  private final ProductionFinishingService service;

  @PostMapping("/api/production/print-batches/{id}/cancel-queued")
  public Object cancelQueued(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.cancelQueued(id, key, body));
  }

  @PostMapping("/api/production/print-batches/{id}/start")
  public Object start(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.start(id, key, body));
  }

  @PostMapping("/api/production/print-batches/{id}/observations")
  public Object observe(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.observe(id, key, body));
  }

  @PostMapping("/api/production/print-batches/{id}/finish")
  public Object finish(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.finish(id, key, body));
  }

  @PostMapping("/api/production/tasks/{id}/post-processing")
  public Object postProcess(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.postProcess(id, key, body));
  }

  @PostMapping("/api/production/tasks/{id}/quality-check")
  public Object qc(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.qualityCheck(id, key, body));
  }

  @GetMapping("/api/admin/production-compensation")
  public Object compensation() {
    return ApiResponse.ok(service.compensation());
  }

  @PostMapping("/api/admin/production-compensation")
  public Object compensation(
      @RequestHeader("Idempotency-Key") String key, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.configureCompensation(key, body));
  }

  @GetMapping("/api/admin/production-settlements")
  public Object settlements() {
    return ApiResponse.ok(service.settlements());
  }
}
