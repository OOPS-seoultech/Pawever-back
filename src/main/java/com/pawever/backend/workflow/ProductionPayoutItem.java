package com.pawever.backend.workflow;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "production_payout_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionPayoutItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long payoutBatchId;

  @Column(nullable = false, unique = true)
  private Long settlementId;

  @Column(nullable = false)
  private int amountKrw;

  public static ProductionPayoutItem of(Long batchId, ProductionSettlement settlement) {
    var item = new ProductionPayoutItem();
    item.payoutBatchId = batchId;
    item.settlementId = settlement.getId();
    item.amountKrw = settlement.getAmountKrw();
    return item;
  }
}
