package com.pawever.backend.pet.repository;

import com.pawever.backend.pet.entity.Pet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PetRepository extends JpaRepository<Pet, Long> {

    Optional<Pet> findByInviteCode(String inviteCode);
}
