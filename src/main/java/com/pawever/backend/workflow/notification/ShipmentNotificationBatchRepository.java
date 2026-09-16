package com.pawever.backend.workflow.notification;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentNotificationBatchRepository
    extends JpaRepository<ShipmentNotificationBatch, Long> {
  Optional<ShipmentNotificationBatch> findByPostalImportBatchId(Long postalImportBatchId);
}
