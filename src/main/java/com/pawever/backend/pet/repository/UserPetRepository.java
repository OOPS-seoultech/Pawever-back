package com.pawever.backend.pet.repository;

import com.pawever.backend.pet.entity.UserPet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserPetRepository extends JpaRepository<UserPet, Long> {

    List<UserPet> findByUserId(Long userId);

    Optional<UserPet> findByUserIdAndPetId(Long userId, Long petId);

    List<UserPet> findByPetId(Long petId);

    Optional<UserPet> findByPetIdAndIsOwnerTrue(Long petId);

    boolean existsByUserIdAndPetId(Long userId, Long petId);

    boolean existsByUserIdAndIsOwnerTrue(Long userId);

    long countByUserIdAndIsOwnerFalse(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update UserPet userPet
           set userPet.memorialLastReadAt = :readAt
         where userPet.user.id = :userId
           and userPet.pet.id = :petId
        """)
    int updateMemorialLastReadAt(
            @Param("userId") Long userId,
            @Param("petId") Long petId,
            @Param("readAt") LocalDateTime readAt
    );
}
