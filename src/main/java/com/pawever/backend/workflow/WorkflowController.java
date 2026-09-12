package com.pawever.backend.workflow;

import com.pawever.backend.global.common.ApiResponse;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class WorkflowController {
  private final WorkflowService service;

  @GetMapping("/api/admin/me")
  public Object me() {
    return ApiResponse.ok(service.me());
  }

  @GetMapping("/api/admin/workflow/orders")
  public Object queue() {
    return ApiResponse.ok(service.queue());
  }

  @GetMapping("/api/production/my-tasks")
  public Object mine() {
    return ApiResponse.ok(service.mine());
  }

  @GetMapping("/api/admin/orders/{number}/workflow")
  public Object detail(@PathVariable String number) {
    return ApiResponse.ok(service.detail(number));
  }

  @GetMapping("/api/admin/orders/{number}/workflow/timeline")
  public Object timeline(@PathVariable String number) {
    return ApiResponse.ok(service.timeline(number));
  }

  @PostMapping("/api/admin/orders/{number}/payments/confirm")
  public Object confirm(
      @PathVariable String number,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(service.confirm(number, key, b));
  }

  @PostMapping("/api/admin/orders/{number}/workflow/enroll")
  public Object enroll(
      @PathVariable String number,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(service.enroll(number, key, b));
  }

  @PostMapping("/api/admin/orders/{number}/workflow/assign")
  public Object assign(
      @PathVariable String number,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(service.assign(number, key, b));
  }

  @PostMapping("/api/production/tasks/{taskId}/start")
  public Object start(
      @PathVariable Long taskId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(service.start(taskId, key, b));
  }

  @PostMapping("/api/production/tasks/{taskId}/complete")
  public Object complete(
      @PathVariable Long taskId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(service.complete(taskId, key, b));
  }

  @GetMapping("/api/admin/workflow/default-assignees")
  public Object defaults() {
    return ApiResponse.ok(service.defaults());
  }

  @PatchMapping("/api/admin/workflow/default-assignees")
  public Object defaults(
      @RequestHeader("Idempotency-Key") String key, @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(service.setDefaults(key, b));
  }

  @GetMapping("/api/admin/workflow/staff")
  public Object staff() {
    return ApiResponse.ok(service.staff());
  }

  @PatchMapping("/api/admin/workflow/staff/{id}/roles")
  public Object roles(
      @PathVariable Long id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(service.roles(id, key, b));
  }

  @PostMapping("/api/production/tasks/{taskId}/artifacts/upload-requests")
  public Object upload(
      @PathVariable Long taskId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.ok(service.upload(taskId, key, b)));
  }

  @PostMapping("/api/production/artifacts/{id}/confirm")
  public Object confirmArtifact(
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(service.confirmArtifact(id, key, b));
  }

  @PostMapping("/api/production/artifacts/{id}/download-link")
  public Object download(@PathVariable String id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.ok(service.download(id)));
  }
}
