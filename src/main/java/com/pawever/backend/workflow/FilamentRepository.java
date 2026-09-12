package com.pawever.backend.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FilamentRepository extends JpaRepository<Filament, Long> {
  List<Filament> findAllByOrderBySpoolIdAsc();

  boolean existsBySpoolId(String spoolId);
}
