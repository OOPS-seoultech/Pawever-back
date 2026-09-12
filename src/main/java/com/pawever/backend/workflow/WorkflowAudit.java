package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "workflow_audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowAudit {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 80)
  private String resource;

  @Column(nullable = false)
  private Long actorId;

  @Column(nullable = false, length = 80)
  private String action;

  @Column(columnDefinition = "longtext")
  private String beforeValue;

  @Column(columnDefinition = "longtext")
  private String afterValue;

  @Column(length = 300)
  private String reason;

  @Column(nullable = false)
  private Instant createdAt;

  public static WorkflowAudit of(
      String resource,
      Long actor,
      String action,
      String before,
      String after,
      String reason,
      Instant at) {
    var e = new WorkflowAudit();
    e.resource = resource;
    e.actorId = actor;
    e.action = action;
    e.beforeValue = before;
    e.afterValue = after;
    e.reason = reason;
    e.createdAt = at;
    return e;
  }
}
