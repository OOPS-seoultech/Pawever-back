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
    List<GoodsSurveyFulfillment> findByProductionStageAndDeliveryMethodOrderByIdAsc(
        com.pawever.backend.workflow.ProductionStage stage,
        com.pawever.backend.goodssurvey.entity.GoodsDeliveryMethod method);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from GoodsSurveyFulfillment f where f.orderNumber=:number")
    Optional<GoodsSurveyFulfillment> lockByOrderNumber(@Param("number") String number);

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
     * 주문번호 여러 개를 한 번에 찾는다.
     *
     * 묶음 처리가 쓴다. 건마다 따로 찾으면 백 건이면 백 번 다녀온다.
     */
    List<GoodsSurveyFulfillment> findByOrderNumberIn(Collection<String> orderNumbers);

    /**
     * 이 송장번호가 이미 붙어 있는 주문.
     *
     * 같은 번호가 두 주문에 붙으면 둘 중 하나는 실제와 다른 번호를 갖게 되고,
     * 그 고객은 자기 물건을 추적할 수 없다.
     */
    Optional<GoodsSurveyFulfillment> findByTrackingNumber(String trackingNumber);

    /**
     * 여러 상태를 한 번에 센다.
     *
     * 뷰 하나가 상태 여럿을 묶는다. 상태마다 세어 더하면 뷰 다섯 개에
     * 아홉 번을 다녀온다.
     */
    long countByStatusIn(Collection<GoodsOrderStatus> statuses);

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

    /**
     * 두 번째 마리부터 더 잡은 자리 수.
     *
     * 정원은 만들어 보내는 피규어 수로 센다. 한 주문에 두 마리면 두 자리를
     * 쓴다. 그런데 자리 계산은 주문이 아니라 제출된 응답을 세고, 만료·환불로
     * 돌아온 자리를 빼는 규칙이 이미 그 안에 있다. 그 규칙을 다시 쓰면
     * 돌아온 자리를 잘못 세기 쉬워, 기존 계산은 그대로 두고 한 마리를 넘는
     * 만큼만 여기서 더한다.
     *
     * 자리를 놓아 준 상태의 주문은 세지 않는다. 기존 계산과 같은 기준이라야
     * 두 값을 더해 쓸 수 있다.
     */
    @Query("""
            select coalesce(sum(fulfillment.petCount - 1), 0)
            from GoodsSurveyFulfillment fulfillment
            where fulfillment.petCount > 1
              and fulfillment.status not in :releasedStatuses
              and exists (
                  select 1
                  from GoodsSurveyResponse response
                  where response.id = fulfillment.responseId
                    and response.campaignId = :campaignId
                    and response.status = :submitted
              )
            """)
    long countExtraPetAllocations(
            @Param("campaignId") String campaignId,
            @Param("submitted")
            com.pawever.backend.goodssurvey.entity.GoodsSurveyResponseStatus submitted,
            @Param("releasedStatuses") Collection<GoodsOrderStatus> releasedStatuses
    );
}
