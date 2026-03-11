package com.pawever.backend.review.repository;

import com.pawever.backend.review.entity.ServiceReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceReviewRepository extends JpaRepository<ServiceReview, Long> {
}
