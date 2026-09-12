package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(
    name = "production_tasks",
    uniqueConstraints = @UniqueConstraint(columnNames = {"order_number", "stage", "attempt"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionTask {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String orderNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ProductionStage stage;

  @Column(nullable = false)
  private int attempt = 1;

  private Long assigneeId;

  @Column(nullable = false, length = 20)
  private String status = "WAITING";

  private Instant startedAt;
  private Instant completedAt;

  public static ProductionTask create(String number, ProductionStage stage, Long assignee) {
    var t = new ProductionTask();
    t.orderNumber = number;
    t.stage = stage;
    t.assigneeId = assignee;
    return t;
  }

  public void assign(Long id) {
    assigneeId = id;
  }

  public void start(Instant at) {
    status = "IN_PROGRESS";
    startedAt = at;
  }

  public void complete(Instant at) {
    status = "COMPLETED";
    completedAt = at;
  }
}
