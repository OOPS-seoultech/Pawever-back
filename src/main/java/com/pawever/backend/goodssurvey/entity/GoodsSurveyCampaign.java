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

    /**
     * 설문 접수 스위치.
     *
     * 모집 기간과도, 굿즈 정원과도 무관하다. 굿즈가 마감돼도 설문은 계속 받아야 하므로
     * 여는 조건을 굿즈 쪽과 나눠 둔다.
     */
    @Column(nullable = false)
    private boolean surveyOpen;

    /**
     * 굿즈 접수 스위치.
     *
     * 이 값만 보고 판단하지 않도록 게터를 열지 않는다. 정원까지 함께 본
     * {@link #isGoodsAvailable(long)}을 쓴다.
     */
    @Getter(AccessLevel.NONE)
    @Column(nullable = false)
    private boolean goodsOpen;

    /** 모집 기간. 화면 안내용 기록이며 접수 여부를 판단하지 않는다. */
    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    public static GoodsSurveyCampaign create(
            String id,
            int capacity,
            int historicalAllocated,
            Instant startsAt,
            Instant endsAt,
            boolean surveyOpen,
            boolean goodsOpen
    ) {
        GoodsSurveyCampaign campaign = new GoodsSurveyCampaign();
        campaign.id = id;
        campaign.capacity = capacity;
        campaign.historicalAllocated = historicalAllocated;
        campaign.startsAt = startsAt;
        campaign.endsAt = endsAt;
        campaign.surveyOpen = surveyOpen;
        campaign.goodsOpen = goodsOpen;
        return campaign;
    }

    /**
     * 굿즈를 지금 받을 수 있는지.
     *
     * 스위치를 켜도 정원이 차면 닫히고, 스위치가 꺼져 있으면 정원이 얼마가
     * 남았든 열리지 않는다. 마감을 정원 같은 계산값에 맡기면 신청 기록이
     * 하나만 정리돼도 마감된 모집이 다시 열린다.
     */
    public boolean isGoodsAvailable(long activeAllocations) {
        return goodsOpen && remaining(activeAllocations) > 0;
    }

    public int remaining(long activeAllocations) {
        return Math.max(0, capacity - historicalAllocated - Math.toIntExact(activeAllocations));
    }

    public int allocated(long activeAllocations) {
        return Math.min(capacity, historicalAllocated + Math.toIntExact(activeAllocations));
    }
}
