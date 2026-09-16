package com.pawever.backend.workflow.postal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostalImportBatchRepository extends JpaRepository<PostalImportBatch, Long> {

    List<PostalImportBatch> findByOutboundBatchIdOrderByIdDesc(Long outboundBatchId);

    List<PostalImportBatch> findTop20ByOrderByIdDesc();
}
