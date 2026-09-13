package com.pawever.backend.workflow;

import com.pawever.backend.global.common.EncryptedStringConverter;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "shipment_export_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentExportBatch {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long exportedBy;

  @Column(nullable = false)
  private Instant exportedAt;

  @Column(nullable = false)
  private int orderCount;

  @Convert(converter = EncryptedStringConverter.class)
  @Lob
  @Column(columnDefinition = "LONGTEXT")
  private String fileBase64;

  static ShipmentExportBatch create(Long actor, Instant at, int count, byte[] bytes) {
    var b = new ShipmentExportBatch();
    b.exportedBy = actor;
    b.exportedAt = at;
    b.orderCount = count;
    b.fileBase64 = java.util.Base64.getEncoder().encodeToString(bytes);
    return b;
  }

  void purgeFile() {
    fileBase64 = null;
  }
}
