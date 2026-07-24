package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsSurveyStory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoodsSurveyStoryRepository extends JpaRepository<GoodsSurveyStory, Long> {

    Optional<GoodsSurveyStory> findByResponseId(String responseId);
}
