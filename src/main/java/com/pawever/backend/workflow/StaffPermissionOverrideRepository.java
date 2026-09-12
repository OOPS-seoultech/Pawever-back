package com.pawever.backend.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffPermissionOverrideRepository
    extends JpaRepository<StaffPermissionOverride, Long> {
  List<StaffPermissionOverride> findByAccountId(Long id);
}
