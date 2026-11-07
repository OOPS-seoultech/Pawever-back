package com.pawever.backend.workflow;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ProductionSettlementRepository extends JpaRepository<ProductionSettlement, Long> {
  boolean existsByOrderNumber(String number);
  boolean existsByOrderNumberAndPetIndex(String number, int petIndex);

  java.util.List<ProductionSettlement> findAllByOrderByIdDesc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<ProductionSettlement> findByIdIn(Collection<Long> ids);
}
