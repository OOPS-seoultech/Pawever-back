package com.pawever.backend.workflow;

import com.pawever.backend.global.common.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "shipment_export_items",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = "order_number"),
      @UniqueConstraint(columnNames = {"batch_id", "export_row_number"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentExportItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long batchId;

  @Column(nullable = false)
  private Long orderId;

  @Column(nullable = false, length = 20)
  private String orderNumber;

  @Column(name = "export_row_number", nullable = false)
  private int rowNumber;

  @Convert(converter = EncryptedStringConverter.class)
  @Lob
  @Column(columnDefinition = "LONGTEXT")
  private String snapshotJson;

  @Column(length = 64)
  private String fingerprint;

  static ShipmentExportItem create(
      Long batch, Long order, String number, int row, String json, String hash) {
    var i = new ShipmentExportItem();
    i.batchId = batch;
    i.orderId = order;
    i.orderNumber = number;
    i.rowNumber = row;
    i.snapshotJson = json;
    i.fingerprint = hash;
    return i;
  }

  void purgeSnapshot() {
    snapshotJson = null;
    fingerprint = null;
  }
}
