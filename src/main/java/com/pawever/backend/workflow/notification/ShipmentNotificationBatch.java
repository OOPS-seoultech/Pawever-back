package com.pawever.backend.workflow.notification;

import com.pawever.backend.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 한 우체국 붙여넣기 기록에서 확정된 알림 수신자 묶음. */
@Entity
@Table(name = "shipment_notification_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentNotificationBatch extends BaseTimeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private Long postalImportBatchId;

  @Column(nullable = false)
  private Long createdBy;

  @Column(nullable = false)
  private Instant queuedAt;

  public static ShipmentNotificationBatch of(Long postalImportBatchId, Long createdBy, Instant at) {
    var batch = new ShipmentNotificationBatch();
    batch.postalImportBatchId = postalImportBatchId;
    batch.createdBy = createdBy;
    batch.queuedAt = at;
    return batch;
  }
}
