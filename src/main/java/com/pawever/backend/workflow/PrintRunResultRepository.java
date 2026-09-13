package com.pawever.backend.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintRunResultRepository extends JpaRepository<PrintRunResult, Long> {
  java.util.List<PrintRunResult> findByBatchIdOrderByIdAsc(Long id);
}
