package com.pawever.backend.mission.repository;

import com.pawever.backend.mission.entity.ChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

    List<ChecklistItem> findAllByOrderByOrderIndexAsc();
}
