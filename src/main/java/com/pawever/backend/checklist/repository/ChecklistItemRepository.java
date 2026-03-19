package com.pawever.backend.checklist.repository;

import com.pawever.backend.checklist.entity.ChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

    List<ChecklistItem> findAllByOrderByOrderIndexAsc();
}
