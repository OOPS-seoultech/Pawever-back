package com.pawever.backend.funeral.repository;

import com.pawever.backend.funeral.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByFuneralCompanyIdOrderByCreatedAtDesc(Long funeralCompanyId);

    List<Review> findByUserId(Long userId);
}
