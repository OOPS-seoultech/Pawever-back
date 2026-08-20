package com.pawever.backend.goodssurvey.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import com.pawever.backend.global.common.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "goods_survey_fulfillments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsSurveyFulfillment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String responseId;

    @Column(nullable = false, unique = true, length = 80)
    private String idempotencyKey;

    @Column(nullable = false, unique = true, length = 80)
    private String conversionEventId;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String trackingJson;

    @Column(nullable = false, length = 30)
    private String goodsType;

    @Column(length = 500)
    private String customGoods;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 1000)
    private String petName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 1000)
    private String guardianName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 1000)
    private String phone;

    @Column(nullable = false, unique = true, length = 88)
    private String phoneHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 1000)
    private String postalCode;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 2000)
    private String address;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 2000)
    private String addressDetail;

    @Column(nullable = false, length = 30)
    private String privacyConsentVersion;

    @Column(nullable = false)
    private Instant privacyConsentedAt;

    /**
     * 설문에 답하고 온 신청인지.
     *
     * 제출 시점의 응답 상태로 정한다. 제출하면 둘 다 SUBMITTED 가 되어 나중에는
     * 구분할 수 없으므로, 그때 확정해 여기에 남긴다.
     */
    @Column(nullable = false)
    private boolean surveyParticipant;

    /** 청구할 금액. 문자로 계좌를 보낼 때 이 값을 그대로 쓴다. */
    @Column(nullable = false)
    private int appliedPriceKrw;

    /** 입금을 확인한 시각. 수동 확인이라 사람이 찍는다. */
    private Instant paidAt;

    private Instant deliveryCompletedAt;

    /** 사진과 상세주소를 지울 때. 계약 기록은 더 오래 남긴다. */
    private Instant deleteAfter;

    /**
     * 계약 기록을 지울 때.
     *
     * 유료 판매는 전자상거래법이 대금 결제와 재화 공급 기록을 5년 보존하도록
     * 한다. 사진과 상세주소는 그 기록이 아니라 제작·배송에만 쓰이므로 먼저 지운다.
     */
    private Instant contractDeleteAfter;

    public static GoodsSurveyFulfillment create(
            String responseId,
            String idempotencyKey,
            String conversionEventId,
            String trackingJson,
            String goodsType,
            String customGoods,
            String petName,
            String guardianName,
            String phone,
            String phoneHash,
            String postalCode,
            String address,
            String addressDetail,
            String privacyConsentVersion,
            Instant privacyConsentedAt,
            boolean surveyParticipant,
            int appliedPriceKrw,
            int contractRetentionDays
    ) {
        GoodsSurveyFulfillment fulfillment = new GoodsSurveyFulfillment();
        fulfillment.responseId = responseId;
        fulfillment.idempotencyKey = idempotencyKey;
        fulfillment.conversionEventId = conversionEventId;
        fulfillment.trackingJson = trackingJson;
        fulfillment.goodsType = goodsType;
        fulfillment.customGoods = customGoods;
        fulfillment.petName = petName;
        fulfillment.guardianName = guardianName;
        fulfillment.phone = phone;
        fulfillment.phoneHash = phoneHash;
        fulfillment.postalCode = postalCode;
        fulfillment.address = address;
        fulfillment.addressDetail = addressDetail;
        fulfillment.privacyConsentVersion = privacyConsentVersion;
        fulfillment.privacyConsentedAt = privacyConsentedAt;
        fulfillment.surveyParticipant = surveyParticipant;
        fulfillment.appliedPriceKrw = appliedPriceKrw;
        // 보존 기간은 주문 시점부터 센다. 배송 표시를 놓친 건도 법정 기간만큼
        // 남아야 하고, 전자상거래법도 거래 시점을 기준으로 삼는다.
        fulfillment.contractDeleteAfter = privacyConsentedAt.plus(contractRetentionDays, ChronoUnit.DAYS);
        return fulfillment;
    }

    /** 입금을 확인했다고 표시한다. 이미 확인한 건은 시각을 바꾸지 않는다. */
    public void markPaid(Instant paidAt) {
        if (this.paidAt != null) {
            return;
        }
        this.paidAt = paidAt;
    }

    public void markDeliveryCompleted(Instant completedAt, int retentionDays) {
        this.deliveryCompletedAt = completedAt;
        this.deleteAfter = completedAt.plus(retentionDays, ChronoUnit.DAYS);
    }

    /** 아직 법정 보존 기간이 남아 있는지. 남아 있으면 행을 지우면 안 된다. */
    public boolean isContractRetained(Instant now) {
        return contractDeleteAfter != null && contractDeleteAfter.isAfter(now);
    }

    /**
     * 사진·상세주소를 지운 뒤 계약 기록만 남긴다.
     *
     * 상세주소는 배송이 끝나면 쓸 일이 없다. 주문·결제와 어디로 보냈는지는
     * 법정 보존 대상이라 남기고, 문 앞 몇 호인지까지는 들고 있지 않는다.
     */
    public void stripDeliveryDetails() {
        this.addressDetail = null;
        this.deleteAfter = null;
    }
}
