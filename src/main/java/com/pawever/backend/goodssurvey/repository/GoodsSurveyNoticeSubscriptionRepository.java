package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsSurveyNoticeSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsSurveyNoticeSubscriptionRepository
        extends JpaRepository<GoodsSurveyNoticeSubscription, Long> {

    boolean existsByEmailHash(String emailHash);
}
