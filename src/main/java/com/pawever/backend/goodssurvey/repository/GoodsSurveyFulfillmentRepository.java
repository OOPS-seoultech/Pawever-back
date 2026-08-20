package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
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

    /**
     * 법정 보존 기간까지 지난 계약 기록.
     *
     * 사진·상세주소를 지울 때와 기준일이 다르다. 그쪽은 배송 후 90일,
     * 이쪽은 전자상거래법이 요구하는 5년이다.
     */
    List<GoodsSurveyFulfillment> findByContractDeleteAfterNotNullAndContractDeleteAfterLessThanEqual(
            Instant now,
            Limit limit
    );

    Optional<GoodsSurveyFulfillment> findByOrderNumber(String orderNumber);

    /**
     * 관리자 목록에 쓸 주문.
     *
     * 이름과 연락처는 암호화해 저장해 데이터베이스에서 찾을 수 없다. 상태로
     * 좁힌 뒤 검색어는 꺼내서 맞춰 본다.
     */
    List<GoodsSurveyFulfillment> findByStatusInOrderByCreatedAtDesc(
            Collection<GoodsOrderStatus> statuses
    );

    /** 결제를 기다리다 시간이 지난 주문. */
    List<GoodsSurveyFulfillment> findByStatusAndPaymentExpiresAtLessThanEqual(
            GoodsOrderStatus status,
            Instant now,
            Limit limit
    );
}
