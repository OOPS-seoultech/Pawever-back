package com.pawever.backend.memorial.repository;

import com.pawever.backend.memorial.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByMemorialIdOrderByCreatedAtDesc(Long memorialId);
}
