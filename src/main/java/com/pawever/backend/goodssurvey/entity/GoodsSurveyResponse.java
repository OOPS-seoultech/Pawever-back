package com.pawever.backend.goodssurvey.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "goods_survey_responses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsSurveyResponse extends BaseTimeEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 50)
    private String campaignId;

    @Column(nullable = false, length = 50)
    private String questionnaireVersion;

    @Column(nullable = false, length = 88)
    private String editTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GoodsSurveyResponseStatus status;

    @Column(nullable = false, length = 30)
    private String selectedGoods;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String answersJson;

    @Column(length = 30)
    private String currentQuestionId;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String trackingJson;

    private Long surveyActiveMs;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String questionTimingsJson;

    private Instant completedAt;

    private Instant reservationExpiresAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static GoodsSurveyResponse draft(
            String id,
            String campaignId,
            String questionnaireVersion,
            String editTokenHash,
            String selectedGoods,
            String trackingJson
    ) {
        GoodsSurveyResponse response = new GoodsSurveyResponse();
        response.id = id;
        response.campaignId = campaignId;
        response.questionnaireVersion = questionnaireVersion;
        response.editTokenHash = editTokenHash;
        response.status = GoodsSurveyResponseStatus.DRAFT;
        response.selectedGoods = selectedGoods;
        response.answersJson = "{}";
        response.trackingJson = trackingJson;
        response.questionTimingsJson = "{}";
        response.surveyActiveMs = 0L;
        return response;
    }

    public void saveDraft(
            String answersJson,
            String currentQuestionId,
            long surveyActiveMs,
            String questionTimingsJson,
            String trackingJson
    ) {
        if (status != GoodsSurveyResponseStatus.DRAFT) {
            return;
        }
        this.answersJson = answersJson;
        this.currentQuestionId = currentQuestionId;
        this.surveyActiveMs = surveyActiveMs;
        this.questionTimingsJson = questionTimingsJson;
        this.trackingJson = trackingJson;
    }

    public void reserve(Instant completedAt, Instant reservationExpiresAt) {
        this.status = GoodsSurveyResponseStatus.RESERVED;
        this.completedAt = completedAt;
        this.reservationExpiresAt = reservationExpiresAt;
    }

    public void completeWithoutSlot(Instant completedAt) {
        this.status = GoodsSurveyResponseStatus.COMPLETED_NO_SLOT;
        this.completedAt = completedAt;
        this.reservationExpiresAt = null;
    }

    public void terminate(Instant completedAt) {
        this.status = GoodsSurveyResponseStatus.TERMINATED;
        this.completedAt = completedAt;
        this.reservationExpiresAt = null;
    }

    /**
     * 설문을 건너뛰고 바로 신청하러 간다.
     *
     * 예약 만료를 두지 않는다. 설문 예약은 선착순 자리를 다투던 때의 장치인데,
     * 직행에는 답할 문항이 없어 시간을 잴 것이 없다.
     */
    public void startDirectPurchase(Instant startedAt) {
        this.status = GoodsSurveyResponseStatus.DIRECT;
        this.completedAt = startedAt;
        this.reservationExpiresAt = null;
    }

    /** 설문에 답하고 온 신청인지. 적용가가 갈리는 기준이다. */
    public boolean isSurveyParticipant() {
        return status != GoodsSurveyResponseStatus.DIRECT;
    }

    public void submit() {
        this.status = GoodsSurveyResponseStatus.SUBMITTED;
        this.reservationExpiresAt = null;
    }

    public boolean hasActiveReservation(Instant now) {
        return status == GoodsSurveyResponseStatus.RESERVED
                && reservationExpiresAt != null
                && reservationExpiresAt.isAfter(now);
    }
}
