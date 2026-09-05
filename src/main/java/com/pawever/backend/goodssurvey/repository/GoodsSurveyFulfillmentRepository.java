package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GoodsSurveyFulfillmentRepository extends JpaRepository<GoodsSurveyFulfillment, Long> {

    Optional<GoodsSurveyFulfillment> findByResponseId(String responseId);

    /**
     * 이 번호로 살아 있는 주문이 있는지.
     *
     * 상태를 보지 않고 번호만 세면, 결제가 만료되거나 취소된 사람도 다시
     * 살 수 없다. 자리는 돌아오는데(countSubmittedAllocations) 그 자리를
     * 그 사람만 못 쓴다 — 현장에서 48시간 안에 입금하지 못한 사람이 정확히
     * 이 경우다.
     *
     * 자리를 놓은 상태는 번호도 함께 놓는다.
     */
    @Query("""
            select count(fulfillment) > 0
            from GoodsSurveyFulfillment fulfillment
            where fulfillment.phoneHash = :phoneHash
              and fulfillment.status not in :releasedStatuses
            """)
    boolean existsLiveByPhoneHash(
            @Param("phoneHash") String phoneHash,
            @Param("releasedStatuses") Collection<GoodsOrderStatus> releasedStatuses
    );

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

    /**
     * 상태별 전체 건수.
     *
     * 목록 위 요약 카드가 쓴다. 목록과 달리 화면이 건 필터를 따라가지 않는다 —
     * 카드는 "오늘 몇 건 챙겨야 하는가"이지 "지금 보고 있는 목록이 몇 건인가"가
     * 아니다. 제작 중만 켜 뒀다고 결제 완료가 0 으로 보이면 입금 확인할 것이
     * 없다고 읽힌다.
     */
    long countByStatus(GoodsOrderStatus status);

    /** 결제를 기다리다 시간이 지난 주문. */
    List<GoodsSurveyFulfillment> findByStatusAndPaymentExpiresAtLessThanEqual(
            GoodsOrderStatus status,
            Instant now,
            Limit limit
    );
}
