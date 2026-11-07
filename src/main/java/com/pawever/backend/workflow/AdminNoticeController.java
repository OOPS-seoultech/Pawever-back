package com.pawever.backend.workflow;

import com.pawever.backend.global.common.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {
  private final AdminNoticeService service;
  @GetMapping public Object list() { return ApiResponse.ok(service.list()); }
  @GetMapping("/onboarding") public Object firstVisit() { return ApiResponse.ok(service.firstVisit()); }
  @PostMapping("/onboarding") public Object decide(@RequestHeader("Idempotency-Key") String key, @RequestBody Map<String,Object> body) { return ApiResponse.ok(service.decide(key, body)); }
  @GetMapping("/{id}") public Object detail(@PathVariable Long id) { return ApiResponse.ok(service.detail(id)); }
  @PostMapping public Object create(@RequestHeader("Idempotency-Key") String key, @RequestBody Map<String,Object> body) { return ApiResponse.ok(service.save(null, key, body)); }
  @PutMapping("/{id}") public Object edit(@PathVariable Long id, @RequestHeader("Idempotency-Key") String key, @RequestBody Map<String,Object> body) { return ApiResponse.ok(service.save(id, key, body)); }
}
