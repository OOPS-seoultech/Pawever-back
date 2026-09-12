package com.pawever.backend.workflow;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionTaskRepository extends JpaRepository<ProductionTask, Long> {
  List<ProductionTask> findByOrderNumberOrderByIdAsc(String number);

  List<ProductionTask> findByAssigneeIdAndStatusNotOrderByIdAsc(Long id, String status);

  boolean existsByOrderNumberAndAssigneeId(String number, Long id);
}
