package com.pawever.backend.workflow.notification;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/notification-batches")
public class ShipmentNotificationController {
  private final ShipmentNotificationService service;

  @GetMapping("/{id}")
  public Map<String, Object> read(@PathVariable Long id) {
    return service.read(id);
  }
}
