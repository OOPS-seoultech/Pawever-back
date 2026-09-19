package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "production_settlements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionSettlement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 20)
  private String orderNumber;

  @Column(nullable = false, unique = true)
  private Long qcTaskId;

  @Column(nullable = false)
  private Long workerId;

  @Column(nullable = false)
  private int amountKrw = 3000;

  @Column(nullable = false, length = 20)
  private String paymentStatus = "UNPAID";

  @Column(nullable = false)
  private Instant createdAt;

  public static ProductionSettlement of(ProductionTask task, Long worker, Instant at) {
    var r = new ProductionSettlement();
    r.orderNumber = task.getOrderNumber();
    r.qcTaskId = task.getId();
    r.workerId = worker;
    r.createdAt = at;
    return r;
  }

  public void preparePayout() {
    if (!"UNPAID".equals(paymentStatus))
      throw new WorkflowException(409, "SETTLEMENT_NOT_UNPAID", "미지급 정산 항목만 지급 준비에 넣을 수 있습니다.");
    paymentStatus = "PREPARED";
  }

  public void markPaid() {
    if (!"PREPARED".equals(paymentStatus))
      throw new WorkflowException(409, "SETTLEMENT_NOT_PREPARED", "지급 준비된 정산 항목만 이체 완료로 기록할 수 있습니다.");
    paymentStatus = "PAID";
  }
}
