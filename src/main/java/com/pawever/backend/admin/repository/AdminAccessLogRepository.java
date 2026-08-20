package com.pawever.backend.admin.repository;

import com.pawever.backend.admin.entity.AdminAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAccessLogRepository extends JpaRepository<AdminAccessLog, Long> {

    List<AdminAccessLog> findByOrderNumberOrderByAccessedAtDesc(String orderNumber);
}
