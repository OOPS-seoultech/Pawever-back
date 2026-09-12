package com.pawever.backend.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelReviewRepository extends JpaRepository<ModelReview, Long> {
  List<ModelReview> findByOrderNumberOrderByIdAsc(String number);
}
