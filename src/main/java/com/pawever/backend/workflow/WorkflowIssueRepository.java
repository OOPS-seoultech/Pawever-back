package com.pawever.backend.workflow;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowIssueRepository extends JpaRepository<WorkflowIssue, Long> {
  List<WorkflowIssue> findByOrderNumber(String number);
}
