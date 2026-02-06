package com.pawever.backend.pet.repository;

import com.pawever.backend.pet.entity.AnimalType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnimalTypeRepository extends JpaRepository<AnimalType, Long> {
}
