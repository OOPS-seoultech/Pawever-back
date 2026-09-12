package com.pawever.backend.workflow;

import com.pawever.backend.global.common.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/production/print-batches")
public class PrintBatchController {
  private final PrintBatchService service;

  @GetMapping
  public Object list() {
    return ApiResponse.ok(service.list());
  }

  @GetMapping("/candidates")
  public Object candidates() {
    return ApiResponse.ok(service.candidates());
  }

  @GetMapping("/staff")
  public Object staff() {
    return ApiResponse.ok(service.staff());
  }

  @GetMapping("/{id}")
  public Object detail(@PathVariable Long id) {
    return ApiResponse.ok(service.detail(id));
  }

  @PostMapping
  public Object create(
      @RequestHeader("Idempotency-Key") String key, @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(service.save(null, key, b));
  }

  @PostMapping("/{id}")
  public Object save(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(service.save(id, key, b));
  }

  @PostMapping("/{id}/confirm")
  public Object confirm(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(service.confirm(id, key, b));
  }

  @PostMapping("/{id}/cancel")
  public Object cancel(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(service.cancel(id, key, b));
  }

  @PostMapping("/{id}/assign")
  public Object assign(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(service.assign(id, key, b));
  }

  @PostMapping("/{id}/artifacts/upload-requests")
  public Object upload(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.ok(service.upload(id, key, b)));
  }

  @PostMapping("/{id}/artifacts/{fileId}/confirm")
  public Object confirmFile(
      @PathVariable Long id,
      @PathVariable String fileId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(service.confirmFile(id, fileId, key, b));
  }

  @PostMapping("/{id}/artifacts/{fileId}/download-link")
  public Object download(@PathVariable Long id, @PathVariable String fileId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.ok(service.download(id, fileId)));
  }
}
