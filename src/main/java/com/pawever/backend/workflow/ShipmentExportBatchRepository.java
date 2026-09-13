package com.pawever.backend.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentExportBatchRepository extends JpaRepository<ShipmentExportBatch, Long> {
  List<ShipmentExportBatch> findAllByOrderByIdDesc();
}
