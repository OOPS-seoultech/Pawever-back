package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(
    name = "order_filament_mappings",
    uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "part_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderFilamentMapping {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String orderNumber;

  @Column(nullable = false)
  private Long taskId;

  @Column(nullable = false)
  private int modelingAttempt;

  @Column(nullable = false, length = 60)
  private String partName;

  @Column(nullable = false, length = 60)
  private String partKey;

  @Column(nullable = false)
  private Long filamentId;

  @Column(nullable = false, length = 64)
  private String spoolId;

  @Column(nullable = false, length = 80)
  private String colorName;

  @Column(nullable = false, length = 40)
  private String material;

  @Column(nullable = false, length = 40)
  private String finish;

  @Column(nullable = false)
  private Long savedBy;

  @Column(nullable = false)
  private Instant savedAt;

  private Instant completedAt;

  public static OrderFilamentMapping record(
      ProductionTask task,
      String part,
      String partKey,
      Filament filament,
      Long actor,
      Instant at,
      boolean complete) {
    var m = new OrderFilamentMapping();
    m.orderNumber = task.getOrderNumber();
    m.taskId = task.getId();
    m.modelingAttempt = task.getAttempt();
    m.partName = part;
    m.partKey = partKey;
    m.filamentId = filament.getId();
    m.spoolId = filament.getSpoolId();
    m.colorName = filament.getColorName();
    m.material = filament.getMaterial();
    m.finish = filament.getFinish();
    m.savedBy = actor;
    m.savedAt = at;
    m.completedAt = complete ? at : null;
    return m;
  }
}
