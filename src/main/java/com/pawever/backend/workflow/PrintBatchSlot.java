package com.pawever.backend.workflow;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "print_batch_slots",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"batch_id", "slot_label"}),
      @UniqueConstraint(columnNames = {"batch_id", "filament_id"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintBatchSlot {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long batchId;

  @Column(nullable = false, length = 30)
  private String slotLabel;

  @Column(nullable = false)
  private Long filamentId;

  public static PrintBatchSlot of(Long batch, String label, Long filament) {
    var s = new PrintBatchSlot();
    s.batchId = batch;
    s.slotLabel = label;
    s.filamentId = filament;
    return s;
  }
}
