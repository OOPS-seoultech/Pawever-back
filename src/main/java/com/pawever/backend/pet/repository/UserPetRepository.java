package com.pawever.backend.pet.repository;

import com.pawever.backend.pet.entity.UserPet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPetRepository extends JpaRepository<UserPet, Long> {

    List<UserPet> findByUserId(Long userId);

    Optional<UserPet> findByUserIdAndPetId(Long userId, Long petId);

    List<UserPet> findByPetId(Long petId);

    Optional<UserPet> findByPetIdAndIsOwnerTrue(Long petId);

    boolean existsByUserIdAndPetId(Long userId, Long petId);
}
