package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "model_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModelReview {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String orderNumber;

  @Column(nullable = false, unique = true)
  private Long reviewTaskId;

  @Column(nullable = false)
  private Long modelingTaskId;

  @Column(nullable = false)
  private int modelingAttempt;

  @Column(nullable = false)
  private Long reviewerId;

  @Column(nullable = false, length = 30)
  private String decision;

  @Column(length = 30)
  private String reasonCode;

  @Column(nullable = false, length = 300)
  private String note;

  @Column(length = 100)
  private String approvedChecks;

  @Column(nullable = false)
  private Instant reviewedAt;

  public static ModelReview record(
      ProductionTask review,
      ProductionTask modeling,
      Long actor,
      String decision,
      String reason,
      String note,
      String checks,
      Instant at) {
    var r = new ModelReview();
    r.orderNumber = review.getOrderNumber();
    r.reviewTaskId = review.getId();
    r.modelingTaskId = modeling.getId();
    r.modelingAttempt = modeling.getAttempt();
    r.reviewerId = actor;
    r.decision = decision;
    r.reasonCode = reason;
    r.note = note;
    r.approvedChecks = checks;
    r.reviewedAt = at;
    return r;
  }
}
