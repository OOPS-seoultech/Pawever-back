package com.pawever.backend.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinishingRecordRepository extends JpaRepository<FinishingRecord, Long> {
  java.util.List<FinishingRecord> findByOrderNumberOrderByIdAsc(String number);
}
