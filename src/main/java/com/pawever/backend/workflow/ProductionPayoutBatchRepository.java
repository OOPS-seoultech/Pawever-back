package com.pawever.backend.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionPayoutBatchRepository extends JpaRepository<ProductionPayoutBatch, Long> {
  List<ProductionPayoutBatch> findAllByOrderByIdDesc();
}
