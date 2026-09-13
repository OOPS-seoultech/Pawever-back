package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "production_settlements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionSettlement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 20)
  private String orderNumber;

  @Column(nullable = false, unique = true)
  private Long qcTaskId;

  @Column(nullable = false)
  private Long workerId;

  @Column(nullable = false)
  private int amountKrw = 3000;

  @Column(nullable = false)
  private Instant createdAt;

  public static ProductionSettlement of(ProductionTask task, Long worker, Instant at) {
    var r = new ProductionSettlement();
    r.orderNumber = task.getOrderNumber();
    r.qcTaskId = task.getId();
    r.workerId = worker;
    r.createdAt = at;
    return r;
  }
}
