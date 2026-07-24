package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoodsSurveyFulfillmentRepository extends JpaRepository<GoodsSurveyFulfillment, Long> {

    Optional<GoodsSurveyFulfillment> findByResponseId(String responseId);

    boolean existsByPhoneHash(String phoneHash);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
