package com.pawever.backend.workflow.notification;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentNotificationEventRepository
    extends JpaRepository<ShipmentNotificationEvent, Long> {
  Optional<ShipmentNotificationEvent> findByOrderNumberAndTrackingNumber(
      String orderNumber, String trackingNumber);

  List<ShipmentNotificationEvent> findByBatchIdOrderByIdAsc(Long batchId);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from ShipmentNotificationEvent e where e.id = :id")
  Optional<ShipmentNotificationEvent> lockById(@Param("id") Long id);

  @Query(
      """
      select e.id from ShipmentNotificationEvent e
      where e.status in ('PENDING_CONFIGURATION', 'PENDING')
         or (e.status in ('ACCEPTED', 'UNKNOWN')
             and e.providerMessageId is not null
             and (e.nextCheckAt is null or e.nextCheckAt <= :now))
      order by e.id
      """)
  List<Long> findReady(@Param("now") Instant now, Pageable pageable);
}
