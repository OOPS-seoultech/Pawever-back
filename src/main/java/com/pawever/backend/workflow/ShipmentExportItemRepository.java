package com.pawever.backend.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentExportItemRepository extends JpaRepository<ShipmentExportItem, Long> {
  List<ShipmentExportItem> findByBatchIdOrderByRowNumberAsc(Long id);

  List<ShipmentExportItem> findBySnapshotJsonIsNotNull();

  boolean existsByOrderNumber(String number);
}
