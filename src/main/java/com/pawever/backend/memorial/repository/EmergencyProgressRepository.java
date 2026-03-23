package com.pawever.backend.memorial.repository;

import com.pawever.backend.memorial.entity.EmergencyProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmergencyProgressRepository extends JpaRepository<EmergencyProgress, Long> {

    Optional<EmergencyProgress> findByPetId(Long petId);

    void deleteByPetId(Long petId);
}
