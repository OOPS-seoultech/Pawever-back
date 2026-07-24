package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponse;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface GoodsSurveyResponseRepository extends JpaRepository<GoodsSurveyResponse, String> {

    @Query("""
            select count(response)
            from GoodsSurveyResponse response
            where response.campaignId = :campaignId
              and (
                    response.status = :submitted
                    or (
                        response.status = :reserved
                        and response.reservationExpiresAt > :now
                    )
              )
            """)
    long countActiveAllocations(
            @Param("campaignId") String campaignId,
            @Param("now") Instant now,
            @Param("reserved") GoodsSurveyResponseStatus reserved,
            @Param("submitted") GoodsSurveyResponseStatus submitted
    );
}
