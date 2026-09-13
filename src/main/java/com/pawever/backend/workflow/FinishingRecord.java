package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "finishing_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinishingRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String orderNumber;

  @Column(nullable = false, unique = true)
  private Long taskId;

  @Column(nullable = false)
  private int attempt;

  @Column(nullable = false, length = 30)
  private String stage;

  @Column(nullable = false, length = 20)
  private String decision;

  @Column(nullable = false, length = 300)
  private String checks;

  @Column(nullable = false, length = 1000)
  private String note;

  @Column(nullable = false, length = 40)
  private String reasonCode;

  @Column(nullable = false, length = 30)
  private String reworkStage;

  @Column(nullable = false, length = 30)
  private String settlementStatus;

  @Column(nullable = false)
  private Long actorId;

  @Column(nullable = false)
  private Instant createdAt;

  public static FinishingRecord of(
      ProductionTask t,
      String decision,
      String checks,
      String note,
      String reason,
      String route,
      String settlement,
      Long actor,
      Instant at) {
    var r = new FinishingRecord();
    r.orderNumber = t.getOrderNumber();
    r.taskId = t.getId();
    r.attempt = t.getAttempt();
    r.stage = t.getStage().name();
    r.decision = decision;
    r.checks = checks;
    r.note = note;
    r.reasonCode = reason;
    r.reworkStage = route;
    r.settlementStatus = settlement;
    r.actorId = actor;
    r.createdAt = at;
    return r;
  }
}
