package com.pawever.backend.goodssurvey.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import com.pawever.backend.global.common.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 2차 제작 안내를 받겠다고 남긴 이메일.
 *
 * 설문 응답과 연결하지 않는다. 설문은 신원 정보를 받지 않고 익명으로 분석한다고
 * 고지하고 받았으므로, 응답 식별자를 함께 두면 그 고지가 거짓이 된다.
 * 굿즈 신청 정보와도 분리한다. 그쪽은 제작·발송 목적이고 보유 기간이 훨씬 짧다.
 */
@Entity
@Table(name = "goods_survey_notice_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsSurveyNoticeSubscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String campaignId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 1000)
    private String email;

    /** 같은 주소가 두 번 들어오는지 확인하려고 둔다. 평문 비교를 하지 않기 위한 값이다. */
    @Column(nullable = false, unique = true, length = 88)
    private String emailHash;

    @Column(nullable = false, length = 30)
    private String consentVersion;

    @Column(nullable = false)
    private Instant consentedAt;

    /**
     * 수신거부를 처리한 시각.
     *
     * 해지는 문의를 받아 사람이 처리한다. 처리했다는 사실을 남길 곳이 없으면
     * 다음 발송 때 같은 사람에게 또 나가므로, 상태만은 여기에 남긴다.
     */
    @Column
    private Instant unsubscribedAt;

    @Column(nullable = false)
    private Instant deleteAfter;

    public static GoodsSurveyNoticeSubscription create(
            String campaignId,
            String email,
            String emailHash,
            String consentVersion,
            Instant consentedAt,
            int retentionDays
    ) {
        GoodsSurveyNoticeSubscription subscription = new GoodsSurveyNoticeSubscription();
        subscription.campaignId = campaignId;
        subscription.email = email;
        subscription.emailHash = emailHash;
        subscription.consentVersion = consentVersion;
        subscription.consentedAt = consentedAt;
        subscription.deleteAfter = consentedAt.plus(retentionDays, ChronoUnit.DAYS);
        return subscription;
    }
}
