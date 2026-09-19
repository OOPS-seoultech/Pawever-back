package com.pawever.backend.workflow;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsCaseRepository extends JpaRepository<AsCase, Long> {
  List<AsCase> findAllByOrderByIdDesc();
}
