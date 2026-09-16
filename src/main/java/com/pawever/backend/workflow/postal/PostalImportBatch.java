package com.pawever.backend.workflow.postal;

import com.pawever.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 우체국 내역을 한 번 붙여넣은 기록.
 *
 * <p>어느 발송 묶음에 대해 붙여넣은 것인지 묶어 둔다. 찾는 범위를 그 묶음 안으로
 * 가두기 위해서다 — 전체 주문을 이름으로 뒤지면 엉뚱한 주문에 송장이 붙는다.
 */
@Entity
@Table(name = "postal_import_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostalImportBatch extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이번에 부친 묶음. shipment_export_batches 의 id 다. */
    @Column(nullable = false)
    private Long outboundBatchId;

    @Column(nullable = false)
    private Long importedBy;

    @Column(nullable = false)
    private Instant importedAt;

    @Column(nullable = false)
    private int rowCount;

    public static PostalImportBatch of(Long outboundBatchId, Long importedBy, Instant at, int rowCount) {
        PostalImportBatch batch = new PostalImportBatch();
        batch.outboundBatchId = outboundBatchId;
        batch.importedBy = importedBy;
        batch.importedAt = at;
        batch.rowCount = rowCount;
        return batch;
    }
}
