package com.pawever.backend.mission.repository;

import com.pawever.backend.mission.entity.PetChecklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PetChecklistRepository extends JpaRepository<PetChecklist, Long> {

    List<PetChecklist> findByPetId(Long petId);

    Optional<PetChecklist> findByPetIdAndChecklistItemId(Long petId, Long checklistItemId);

    long countByPetId(Long petId);

    long countByPetIdAndCompletedTrue(Long petId);
}
