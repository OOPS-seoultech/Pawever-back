package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponse;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponseStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface GoodsSurveyResponseRepository extends JpaRepository<GoodsSurveyResponse, String> {

    /**
     * 선착순 자리를 차지한 건수.
     *
     * 노션 기준은 "설문 제출 + 굿즈 제작 정보 입력을 모두 한 사람"이다.
     * 설문만 끝낸 예약(RESERVED)은 자리를 잡아두지 않으므로 세지 않는다.
     * 예약은 제출 자격을 확인하는 용도로만 남는다.
     *
     * 제출했더라도 주문이 죽었으면 자리를 놓는다. 만료·취소 처리는 주문 쪽
     * 상태만 바꾸고 응답은 SUBMITTED 로 두기 때문에, 여기서 걸러 내지 않으면
     * 결제되지 않아 사라진 주문이 정원을 영원히 잡는다. 2026-08-30 접수된
     * 테스트 주문이 만료된 뒤에도 allocated 가 1 로 남아 있었다.
     *
     * 주문 기록이 아예 없는 제출은 자리를 잡은 것으로 센다. 제출하면 주문이
     * 함께 생기므로 정상 흐름에서는 나오지 않지만, 없을 때 놓아 버리면 정원을
     * 넘겨 파는 쪽으로 넘어진다.
     */
    @Query("""
            select count(response)
            from GoodsSurveyResponse response
            where response.campaignId = :campaignId
              and response.status = :submitted
              and not exists (
                  select 1
                  from GoodsSurveyFulfillment fulfillment
                  where fulfillment.responseId = response.id
                    and fulfillment.status in :releasedStatuses
              )
            """)
    long countSubmittedAllocations(
            @Param("campaignId") String campaignId,
            @Param("submitted") GoodsSurveyResponseStatus submitted,
            @Param("releasedStatuses") Collection<GoodsOrderStatus> releasedStatuses
    );

    /**
     * 보유 기간이 지난 응답.
     *
     * 설문에는 파기 예정일을 따로 두지 않는다. 신원 정보를 받지 않아 응답을
     * 개별로 관리할 일이 없고, 수집 시점만 있으면 기간을 셀 수 있다.
     */
    List<GoodsSurveyResponse> findByCreatedAtLessThanEqual(LocalDateTime threshold, Limit limit);
}
