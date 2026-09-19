package com.pawever.backend.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionPayoutItemRepository extends JpaRepository<ProductionPayoutItem, Long> {
  boolean existsBySettlementId(Long settlementId);

  List<ProductionPayoutItem> findByPayoutBatchIdOrderByIdAsc(Long payoutBatchId);
}
