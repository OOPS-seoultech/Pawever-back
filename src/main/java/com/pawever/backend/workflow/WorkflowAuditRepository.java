package com.pawever.backend.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowAuditRepository extends JpaRepository<WorkflowAudit, Long> {
  List<WorkflowAudit> findByResourceOrderByIdAsc(String resource);
}
