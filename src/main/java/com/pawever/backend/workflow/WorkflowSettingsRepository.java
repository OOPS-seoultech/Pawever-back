package com.pawever.backend.workflow;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface WorkflowSettingsRepository extends JpaRepository<WorkflowSettings, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from WorkflowSettings s where s.id=1")
  Optional<WorkflowSettings> locked();
}
