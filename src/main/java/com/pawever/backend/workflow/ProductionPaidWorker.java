package com.pawever.backend.workflow;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "production_paid_workers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionPaidWorker {
  @Id private Long accountId;

  public static ProductionPaidWorker of(Long id) {
    var w = new ProductionPaidWorker();
    w.accountId = id;
    return w;
  }
}
