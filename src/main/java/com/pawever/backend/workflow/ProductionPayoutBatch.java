package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "production_payout_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionPayoutBatch {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long beneficiaryId;

  @Column(nullable = false)
  private int grossKrw;

  @Column(nullable = false)
  private int deductionKrw;

  @Column(nullable = false)
  private int netKrw;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(nullable = false)
  private Long preparedBy;

  @Column(nullable = false)
  private Instant preparedAt;

  private Long paidBy;
  private Instant paidAt;

  @Column(length = 300)
  private String transferReference;

  public static ProductionPayoutBatch prepare(
      Long beneficiaryId, int grossKrw, int deductionKrw, Long actorId, Instant at) {
    var batch = new ProductionPayoutBatch();
    batch.beneficiaryId = beneficiaryId;
    batch.grossKrw = grossKrw;
    batch.deductionKrw = deductionKrw;
    batch.netKrw = grossKrw - deductionKrw;
    batch.status = "PREPARED";
    batch.preparedBy = actorId;
    batch.preparedAt = at;
    return batch;
  }

  public void markPaid(Long actorId, String reference, Instant at) {
    if (!"PREPARED".equals(status))
      throw new WorkflowException(409, "PAYOUT_NOT_PREPARED", "지급 준비 상태인 묶음만 이체 완료로 기록할 수 있습니다.");
    status = "PAID";
    paidBy = actorId;
    paidAt = at;
    transferReference = reference;
  }
}
