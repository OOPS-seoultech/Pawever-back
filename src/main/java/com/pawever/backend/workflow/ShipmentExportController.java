package com.pawever.backend.workflow;

import com.pawever.backend.global.common.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/shipments")
public class ShipmentExportController {
  private final ShipmentExportService service;

  @GetMapping("/candidates")
  public Object candidates() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.ok(service.candidates()));
  }

  @GetMapping("/pickup-candidates")
  public Object pickupCandidates() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.ok(service.pickupCandidates()));
  }

  @PostMapping("/pickup-completions")
  public Object completePickupPacking(
      @RequestHeader("Idempotency-Key") String key, @RequestBody Map<String, Object> body) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.ok(service.completePickupPacking(key, body)));
  }

  @GetMapping("/export-batches")
  public Object list() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.ok(service.list()));
  }

  @PostMapping("/export-batches")
  public Object create(
      @RequestHeader("Idempotency-Key") String key, @RequestBody Map<String, Object> b) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.ok(service.create(key, b)));
  }

  @GetMapping("/export-batches/{id}/file")
  public ResponseEntity<byte[]> download(@PathVariable Long id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"post-office-" + id + ".xlsx\"")
        .body(service.download(id));
  }
}
