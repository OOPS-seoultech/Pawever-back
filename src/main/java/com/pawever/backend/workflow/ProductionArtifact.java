package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "production_artifacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionArtifact {
  @Id private String id;

  @Column(nullable = false, length = 20)
  private String orderNumber;

  @Column(nullable = false)
  private Long taskId;

  @Column(nullable = false)
  private Long uploaderId;

  @Column(nullable = false, length = 30)
  private String kind;

  @Column(nullable = false, length = 200)
  private String fileName;

  @Column(nullable = false, length = 100)
  private String contentType;

  @Column(nullable = false, unique = true, length = 500)
  private String objectKey;

  @Column(nullable = false)
  private long expectedSize;

  @Column(nullable = false)
  private boolean confirmed;

  @Column(nullable = false)
  private Instant expiresAt;

  public static ProductionArtifact pending(
      String id,
      String number,
      Long task,
      Long uploader,
      String kind,
      String name,
      String type,
      long size,
      Instant expires) {
    var a = new ProductionArtifact();
    a.id = id;
    a.orderNumber = number;
    a.taskId = task;
    a.uploaderId = uploader;
    a.kind = kind;
    a.fileName = name;
    a.contentType = type;
    a.expectedSize = size;
    a.expiresAt = expires;
    a.objectKey = "production/" + number + "/" + id + "/" + name;
    return a;
  }

  public void confirm() {
    confirmed = true;
  }
}
