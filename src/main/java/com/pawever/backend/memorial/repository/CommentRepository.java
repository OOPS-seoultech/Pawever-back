package com.pawever.backend.memorial.repository;

import com.pawever.backend.memorial.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("""
            SELECT c FROM Comment c
            LEFT JOIN FETCH c.authorPet ap
            LEFT JOIN FETCH ap.breed b
            LEFT JOIN FETCH b.animalType
            WHERE c.pet.id = :petId
            ORDER BY c.createdAt DESC
            """)
    List<Comment> findByPetIdOrderByCreatedAtDesc(@Param("petId") Long petId);

    @Query("""
            SELECT COUNT(c)
            FROM Comment c
            WHERE c.pet.id = :petId
              AND c.createdAt > :createdAt
              AND (c.user IS NULL OR c.user.id <> :userId)
            """)
    long countUnreadForPet(@Param("petId") Long petId, @Param("createdAt") LocalDateTime createdAt, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.user = null WHERE c.user.id = :userId")
    int detachUserFromComments(@Param("userId") Long userId);
}
