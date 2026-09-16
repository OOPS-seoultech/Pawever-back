package com.pawever.backend.workflow.postal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostalImportRowRepository extends JpaRepository<PostalImportRow, Long> {

    List<PostalImportRow> findByBatchIdOrderByLineNumberAsc(Long batchId);

    Optional<PostalImportRow> findByIdAndBatchId(Long id, Long batchId);

    /** 이미 반영된 송장인지 본다. 같은 번호가 두 주문에 붙으면 한쪽은 영영 못 찾는다. */
    List<PostalImportRow> findByTrackingNumberAndStatus(String trackingNumber, String status);
}
