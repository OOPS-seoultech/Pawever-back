package com.pawever.backend.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderFilamentMappingRepository extends JpaRepository<OrderFilamentMapping, Long> {
  List<OrderFilamentMapping> findByOrderNumberOrderByIdAsc(String number);

  List<OrderFilamentMapping> findByTaskIdOrderByIdAsc(Long taskId);
}
