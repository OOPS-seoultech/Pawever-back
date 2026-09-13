package com.pawever.backend.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionSettlementRepository extends JpaRepository<ProductionSettlement, Long> {
  boolean existsByOrderNumber(String number);

  java.util.List<ProductionSettlement> findAllByOrderByIdDesc();
}
