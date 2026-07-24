package com.pawever.backend.goodssurvey.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "goods_survey_campaigns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsSurveyCampaign extends BaseTimeEntity {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int historicalAllocated;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    public static GoodsSurveyCampaign create(
            String id,
            int capacity,
            int historicalAllocated,
            Instant startsAt,
            Instant endsAt
    ) {
        GoodsSurveyCampaign campaign = new GoodsSurveyCampaign();
        campaign.id = id;
        campaign.capacity = capacity;
        campaign.historicalAllocated = historicalAllocated;
        campaign.startsAt = startsAt;
        campaign.endsAt = endsAt;
        return campaign;
    }

    public boolean isOpenAt(Instant now) {
        return !now.isBefore(startsAt) && !now.isAfter(endsAt);
    }

    public int remaining(long activeAllocations) {
        return Math.max(0, capacity - historicalAllocated - Math.toIntExact(activeAllocations));
    }

    public int allocated(long activeAllocations) {
        return Math.min(capacity, historicalAllocated + Math.toIntExact(activeAllocations));
    }
}
