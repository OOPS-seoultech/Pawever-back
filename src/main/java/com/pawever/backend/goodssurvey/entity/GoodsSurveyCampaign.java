package com.pawever.backend.goodssurvey.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /**
     * 이 모집을 파는 통로.
     *
     * 값과 정원이 통로마다 다르다. 설정이 아니라 모집에 붙여 두는 이유는,
     * 행사가 끝나 설정을 지워도 이미 이 모집에 붙은 주문은 사람이 동의한
     * 값 그대로 계산돼야 하기 때문이다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoodsSalesChannel channel = GoodsSalesChannel.ONLINE;

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
        return create(
                id, GoodsSalesChannel.ONLINE, capacity, historicalAllocated,
                startsAt, endsAt, surveyOpen, goodsOpen);
    }

    public static GoodsSurveyCampaign create(
            String id,
            GoodsSalesChannel channel,
            int capacity,
            int historicalAllocated,
            Instant startsAt,
            Instant endsAt,
            boolean surveyOpen,
            boolean goodsOpen
    ) {
        GoodsSurveyCampaign campaign = new GoodsSurveyCampaign();
        campaign.id = id;
        campaign.channel = channel;
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
    /**
     * 정원을 두지 않는 모집인지.
     *
     * 1차는 무료 체험단이라 100자리로 끊었지만, 유료로 전환한 뒤에는 수량을
     * 막을 이유가 없다. capacity 를 0 이하로 두면 선착순 계산을 건너뛴다.
     */
    public boolean isUnlimited() {
        return capacity <= 0;
    }

    public boolean isGoodsAvailable(long activeAllocations) {
        return goodsOpen && (isUnlimited() || remaining(activeAllocations) > 0);
    }

    /** 정원이 없으면 남은 자리를 셀 수 없다. 화면에서 표시를 감추도록 -1을 준다. */
    public int remaining(long activeAllocations) {
        if (isUnlimited()) {
            return -1;
        }
        return Math.max(0, capacity - historicalAllocated - Math.toIntExact(activeAllocations));
    }

    public int allocated(long activeAllocations) {
        int total = historicalAllocated + Math.toIntExact(activeAllocations);
        return isUnlimited() ? total : Math.min(capacity, total);
    }
}
