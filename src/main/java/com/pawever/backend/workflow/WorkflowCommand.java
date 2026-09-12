package com.pawever.backend.workflow;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "workflow_commands",
    uniqueConstraints = @UniqueConstraint(columnNames = {"actor_id", "command_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowCommand {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long actorId;

  @Column(nullable = false, length = 100)
  private String commandKey;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  @Column(length = 20)
  private String orderNumber;

  @Convert(converter = com.pawever.backend.global.common.EncryptedStringConverter.class)
  @Lob
  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String resultJson;

  public static WorkflowCommand of(
      Long actor, String key, String hash, String json, String number) {
    var c = new WorkflowCommand();
    c.actorId = actor;
    c.commandKey = key;
    c.fingerprint = hash;
    c.resultJson = json;
    c.orderNumber = number;
    return c;
  }
}
