package com.pawever.backend.workflow;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintBatchItemRepository extends JpaRepository<PrintBatchItem, Long> {
  List<PrintBatchItem> findByBatchIdOrderByIdAsc(Long batchId);

  Optional<PrintBatchItem> findByPlateTaskId(Long taskId);

  List<PrintBatchItem> findByOrderNumberOrderByIdDesc(String number);
}
