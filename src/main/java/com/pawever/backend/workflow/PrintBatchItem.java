package com.pawever.backend.workflow;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "print_batch_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintBatchItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long batchId;

  @Column(nullable = false, length = 20)
  private String orderNumber;

  @Column(nullable = false, unique = true)
  private Long plateTaskId;

  @Column(nullable = false)
  private Long mappingTaskId;

  public static PrintBatchItem of(Long batch, ProductionTask plate, Long mapping) {
    var i = new PrintBatchItem();
    i.batchId = batch;
    i.orderNumber = plate.getOrderNumber();
    i.plateTaskId = plate.getId();
    i.mappingTaskId = mapping;
    return i;
  }
}
