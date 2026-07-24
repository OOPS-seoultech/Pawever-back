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

    private Instant deliveryCompletedAt;

    private Instant deleteAfter;

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
            Instant privacyConsentedAt
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
        return fulfillment;
    }

    public void markDeliveryCompleted(Instant completedAt, int retentionDays) {
        this.deliveryCompletedAt = completedAt;
        this.deleteAfter = completedAt.plus(retentionDays, java.time.temporal.ChronoUnit.DAYS);
    }
}
