package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsSurveyCampaign;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GoodsSurveyCampaignRepository extends JpaRepository<GoodsSurveyCampaign, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select campaign from GoodsSurveyCampaign campaign where campaign.id = :id")
    Optional<GoodsSurveyCampaign> findByIdForUpdate(@Param("id") String id);
}
