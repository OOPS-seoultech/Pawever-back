package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "print_batch_artifacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintBatchArtifact {
  @Id private String id;

  @Column(nullable = false)
  private Long batchId;

  @Column(nullable = false)
  private Long uploaderId;

  @Column(nullable = false)
  private int layoutRevision;

  @Column(nullable = false, length = 200)
  private String fileName;

  @Column(nullable = false, unique = true, length = 500)
  private String objectKey;

  @Column(nullable = false)
  private long expectedSize;

  @Column(nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private boolean confirmed;

  public static PrintBatchArtifact pending(
      PrintBatch batch, Long actor, String name, long size, Instant expires) {
    var a = new PrintBatchArtifact();
    a.id = java.util.UUID.randomUUID().toString();
    a.batchId = batch.getId();
    a.uploaderId = actor;
    a.layoutRevision = batch.getLayoutRevision();
    a.fileName = name;
    a.expectedSize = size;
    a.expiresAt = expires;
    a.objectKey = "production/print-batches/" + batch.getId() + "/" + a.id + "/" + name;
    return a;
  }

  public void confirm() {
    confirmed = true;
  }
}
