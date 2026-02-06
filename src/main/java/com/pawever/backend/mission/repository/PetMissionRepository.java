package com.pawever.backend.mission.repository;

import com.pawever.backend.mission.entity.PetMission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PetMissionRepository extends JpaRepository<PetMission, Long> {

    List<PetMission> findByPetId(Long petId);

    Optional<PetMission> findByPetIdAndMissionId(Long petId, Long missionId);

    long countByPetId(Long petId);

    long countByPetIdAndCompletedTrue(Long petId);
}
