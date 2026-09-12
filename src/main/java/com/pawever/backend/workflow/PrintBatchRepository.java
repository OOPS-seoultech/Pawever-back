package com.pawever.backend.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintBatchRepository extends JpaRepository<PrintBatch, Long> {
  List<PrintBatch> findAllByOrderByIdDesc();
}
