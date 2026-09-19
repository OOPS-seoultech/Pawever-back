package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "as_cases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AsCase {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String orderNumber;

  @Column(nullable = false, length = 1000)
  private String reason;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(nullable = false)
  private Long createdBy;

  @Column(nullable = false)
  private Instant createdAt;

  private Long closedBy;
  private Instant closedAt;

  public static AsCase open(String orderNumber, String reason, Long actorId, Instant now) {
    var value = new AsCase();
    value.orderNumber = orderNumber;
    value.reason = reason;
    value.status = "OPEN";
    value.createdBy = actorId;
    value.createdAt = now;
    return value;
  }

  public boolean isOpen() {
    return "OPEN".equals(status);
  }

  public void close(Long actorId, Instant now) {
    status = "CLOSED";
    closedBy = actorId;
    closedAt = now;
  }
}
