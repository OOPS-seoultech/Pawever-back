package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(
    name = "production_tasks",
    uniqueConstraints = @UniqueConstraint(columnNames = {"order_number", "pet_index", "stage", "attempt"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionTask {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String orderNumber;

  @Column(nullable = false)
  private int petIndex;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ProductionStage stage;

  @Column(nullable = false)
  private int attempt = 1;

  private Long assigneeId;

  private Long materialsFromTaskId;
  public void useMaterialsFrom(Long id) { materialsFromTaskId = id; }

  @Column(nullable = false, length = 20)
  private String status = "WAITING";

  private Instant startedAt;
  private Instant completedAt;

  public static ProductionTask create(String number, ProductionStage stage, Long assignee) {
    return create(number, stage, assignee, 1);
  }

  public static ProductionTask create(
      String number, ProductionStage stage, Long assignee, int attempt) {
    return create(number, 0, stage, assignee, attempt);
  }

  public static ProductionTask create(
      String number, int petIndex, ProductionStage stage, Long assignee, int attempt) {
    var t = new ProductionTask();
    t.petIndex = petIndex;
    t.orderNumber = number;
    t.stage = stage;
    t.assigneeId = assignee;
    t.attempt = attempt;
    return t;
  }

  public void assign(Long id) {
    assigneeId = id;
  }

  public void start(Instant at) {
    status = "IN_PROGRESS";
    startedAt = at;
  }

  public void supersede(Instant at) { status = "SUPERSEDED"; completedAt = at; }

  public void complete(Instant at) {
    status = "COMPLETED";
    completedAt = at;
  }
}
