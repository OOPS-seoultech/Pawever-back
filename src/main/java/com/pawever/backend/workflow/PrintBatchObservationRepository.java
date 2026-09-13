package com.pawever.backend.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintBatchObservationRepository
    extends JpaRepository<PrintBatchObservation, Long> {
  java.util.List<PrintBatchObservation> findByBatchIdOrderByIdAsc(Long id);
}
