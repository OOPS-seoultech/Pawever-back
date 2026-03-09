package com.pawever.backend.mission.repository;

import com.pawever.backend.mission.entity.Mission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MissionRepository extends JpaRepository<Mission, Long> {

    List<Mission> findAllByOrderByOrderIndexAscIdAsc();
}
