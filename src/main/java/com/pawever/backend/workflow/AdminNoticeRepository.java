package com.pawever.backend.workflow;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminNoticeRepository extends JpaRepository<AdminNotice, Long> {
  List<AdminNotice> findAllByOrderByIdDesc();
  Optional<AdminNotice> findByGuideKey(String key);
}
