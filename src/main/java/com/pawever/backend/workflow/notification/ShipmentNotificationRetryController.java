package com.pawever.backend.workflow.notification;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/notification-events")
public class ShipmentNotificationRetryController {
  private final ShipmentNotificationService service;

  @PostMapping("/{id}/retry")
  public Map<String, Object> retry(@PathVariable Long id, @RequestBody RetryRequest request) {
    return service.retry(id, request.reason());
  }

  public record RetryRequest(String reason) {}
}
