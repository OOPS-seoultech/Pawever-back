package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "print_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintBatch {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private long version;

  @Column(nullable = false)
  private Long creatorId;

  @Column(nullable = false, length = 20)
  private String status = "DRAFT";

  @Column(nullable = false, length = 80)
  private String printerName = "";

  private Long printingAssigneeId;

  @Column(nullable = false)
  private int layoutRevision = 1;

  @Column(nullable = false, length = 64)
  private String layoutFingerprint;

  @Column(nullable = false)
  private Instant updatedAt;

  private Instant confirmedAt;
  private Instant startedAt;
  private Instant finishedAt;

  public void start(Instant at) {
    status = "PRINTING";
    startedAt = at;
    touch(at);
  }

  public void finish(Instant at) {
    status = "FINISHED";
    finishedAt = at;
    touch(at);
  }

  @Column(length = 36)
  private String artifactId;

  public static PrintBatch create(Long actor) {
    var p = new PrintBatch();
    p.creatorId = actor;
    return p;
  }

  public void configure(String printer, Long assignee, String fingerprint, Instant at) {
    if (layoutFingerprint != null && !layoutFingerprint.equals(fingerprint)) {
      layoutRevision++;
      artifactId = null;
    }
    printerName = printer;
    printingAssigneeId = assignee;
    layoutFingerprint = fingerprint;
    touch(at);
  }

  public void touch(Instant at) {
    updatedAt = updatedAt != null && !at.isAfter(updatedAt) ? updatedAt.plusNanos(1000) : at;
  }

  public void confirm(Instant at) {
    status = "CONFIRMED";
    confirmedAt = at;
    touch(at);
  }

  public void cancel(Instant at) {
    status = "CANCELED";
    touch(at);
  }

  public void useArtifact(String id, Instant at) {
    artifactId = id;
    touch(at);
  }
}
