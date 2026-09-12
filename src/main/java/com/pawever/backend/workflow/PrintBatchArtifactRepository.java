package com.pawever.backend.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintBatchArtifactRepository extends JpaRepository<PrintBatchArtifact, String> {
  List<PrintBatchArtifact> findByBatchIdOrderByIdDesc(Long batchId);
}
