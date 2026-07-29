package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponse;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoodsSurveyResponseRepository extends JpaRepository<GoodsSurveyResponse, String> {

    /**
     * 선착순 자리를 차지한 건수.
     *
     * 노션 기준은 "설문 제출 + 굿즈 제작 정보 입력을 모두 한 사람"이다.
     * 설문만 끝낸 예약(RESERVED)은 자리를 잡아두지 않으므로 세지 않는다.
     * 예약은 제출 자격을 확인하는 용도로만 남는다.
     */
    @Query("""
            select count(response)
            from GoodsSurveyResponse response
            where response.campaignId = :campaignId
              and response.status = :submitted
            """)
    long countSubmittedAllocations(
            @Param("campaignId") String campaignId,
            @Param("submitted") GoodsSurveyResponseStatus submitted
    );
}
