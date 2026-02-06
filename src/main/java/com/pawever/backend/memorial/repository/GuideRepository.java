package com.pawever.backend.memorial.repository;

import com.pawever.backend.memorial.entity.Guide;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideRepository extends JpaRepository<Guide, Long> {
}
