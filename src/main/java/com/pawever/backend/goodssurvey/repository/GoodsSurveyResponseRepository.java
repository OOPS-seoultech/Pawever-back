package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponse;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponseStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 보유 기간이 지난 응답.
     *
     * 설문에는 파기 예정일을 따로 두지 않는다. 신원 정보를 받지 않아 응답을
     * 개별로 관리할 일이 없고, 수집 시점만 있으면 기간을 셀 수 있다.
     */
    List<GoodsSurveyResponse> findByCreatedAtLessThanEqual(LocalDateTime threshold, Limit limit);
}
