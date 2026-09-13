package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "print_batch_observations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintBatchObservation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long batchId;

  @Column(nullable = false)
  private Long actorId;

  @Column(nullable = false, length = 1000)
  private String note;

  @Column(precision = 10, scale = 2)
  private BigDecimal purgeGrams;

  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String issuesJson;

  @Column(nullable = false)
  private Instant createdAt;

  public static PrintBatchObservation of(
      Long batch, Long actor, String note, BigDecimal grams, String issues, Instant at) {
    var r = new PrintBatchObservation();
    r.batchId = batch;
    r.actorId = actor;
    r.note = note;
    r.purgeGrams = grams;
    r.issuesJson = issues;
    r.createdAt = at;
    return r;
  }
}
