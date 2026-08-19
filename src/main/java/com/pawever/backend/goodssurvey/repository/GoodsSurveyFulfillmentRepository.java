package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GoodsSurveyFulfillmentRepository extends JpaRepository<GoodsSurveyFulfillment, Long> {

    Optional<GoodsSurveyFulfillment> findByResponseId(String responseId);

    boolean existsByPhoneHash(String phoneHash);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * 파기할 때가 된 배송 정보.
     *
     * deleteAfter 는 배송 완료를 표시할 때 채워진다. 아직 배송하지 않은 건은
     * 비어 있고, 여기에 걸리지 않는다.
     */
    List<GoodsSurveyFulfillment> findByDeleteAfterNotNullAndDeleteAfterLessThanEqual(
            Instant now,
            Limit limit
    );
}
