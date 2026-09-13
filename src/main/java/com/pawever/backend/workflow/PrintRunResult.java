package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "print_run_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintRunResult {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long batchId;

  @Column(nullable = false, length = 20)
  private String orderNumber;

  @Column(nullable = false, unique = true)
  private Long taskId;

  @Column(nullable = false)
  private int attempt;

  @Column(nullable = false, length = 20)
  private String result;

  @Column(nullable = false, length = 1000)
  private String note;

  @Column(nullable = false)
  private Long actorId;

  @Column(nullable = false)
  private Instant createdAt;

  public static PrintRunResult of(
      Long batch, ProductionTask t, String result, String note, Long actor, Instant at) {
    var r = new PrintRunResult();
    r.batchId = batch;
    r.orderNumber = t.getOrderNumber();
    r.taskId = t.getId();
    r.attempt = t.getAttempt();
    r.result = result;
    r.note = note;
    r.actorId = actor;
    r.createdAt = at;
    return r;
  }
}
