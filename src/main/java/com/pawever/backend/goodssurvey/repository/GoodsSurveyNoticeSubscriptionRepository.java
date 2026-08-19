package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsSurveyNoticeSubscription;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GoodsSurveyNoticeSubscriptionRepository
        extends JpaRepository<GoodsSurveyNoticeSubscription, Long> {

    boolean existsByEmailHash(String emailHash);

    Optional<GoodsSurveyNoticeSubscription> findByEmailHash(String emailHash);

    /**
     * 파기할 때가 된 안내 이메일.
     *
     * 보유 기간이 지났거나, 수신을 거부한 주소다. 거부한 주소를 남겨 두면
     * 보내지 않겠다고 해 놓고 계속 갖고 있는 셈이 된다.
     */
    @Query("""
            select subscription
            from GoodsSurveyNoticeSubscription subscription
            where subscription.deleteAfter <= :now
               or subscription.unsubscribedAt is not null
            """)
    List<GoodsSurveyNoticeSubscription> findPurgeTargets(
            @Param("now") Instant now,
            Limit limit
    );
}
