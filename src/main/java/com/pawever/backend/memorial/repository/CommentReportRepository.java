package com.pawever.backend.memorial.repository;

import com.pawever.backend.memorial.entity.CommentReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentReportRepository extends JpaRepository<CommentReport, Long> {
}
