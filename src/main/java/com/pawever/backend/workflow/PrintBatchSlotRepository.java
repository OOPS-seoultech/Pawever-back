package com.pawever.backend.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintBatchSlotRepository extends JpaRepository<PrintBatchSlot, Long> {
  List<PrintBatchSlot> findByBatchIdOrderByIdAsc(Long batchId);
}
