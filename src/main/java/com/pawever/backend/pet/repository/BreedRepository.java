package com.pawever.backend.pet.repository;

import com.pawever.backend.pet.entity.Breed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BreedRepository extends JpaRepository<Breed, Long> {

    List<Breed> findByAnimalTypeId(Long animalTypeId);
}
