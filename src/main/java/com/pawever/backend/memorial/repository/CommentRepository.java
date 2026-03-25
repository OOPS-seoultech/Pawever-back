package com.pawever.backend.memorial.repository;

import com.pawever.backend.memorial.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPetIdOrderByCreatedAtDesc(Long petId);

    long countByPetIdAndCreatedAtAfterAndUserIdNot(Long petId, LocalDateTime createdAt, Long userId);
}
