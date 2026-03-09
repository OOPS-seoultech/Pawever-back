package com.pawever.backend.memorial.repository;

import com.pawever.backend.memorial.entity.ReportReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportReasonRepository extends JpaRepository<ReportReason, Long> {

    List<ReportReason> findAllByOrderByOrderIndexAscIdAsc();
}
