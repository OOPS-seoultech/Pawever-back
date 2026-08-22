package com.pawever.backend.goodssurvey.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import com.pawever.backend.global.common.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /**
     * 주문번호. PE-2026-000001 처럼 연도 안에서 센다.
     *
     * 응답 식별자(UUID)는 고객에게 읽어 주기 어렵다. 문의를 받거나 입금을 대조할
     * 때 부를 이름이 따로 필요하다.
     */
    @Column(nullable = false, unique = true, length = 20)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GoodsOrderStatus status;

    /** 정상가. */
    @Column(nullable = false)
    private int listPriceKrw;

    /** 할인액. 없으면 0. */
    @Column(nullable = false)
    private int discountAmountKrw;

    /** 적용한 프로모션 이름. 없으면 비어 있다. */
    @Column(length = 100)
    private String promotionName;

    /**
     * 배송비.
     *
     * 화면에는 제작비와 나눠 보여 주고 결제는 한 번에 받는다. 청구액에 이미
     * 더해져 있지만, 얼마가 배송비였는지 나중에 알아야 해서 따로 적는다.
     */
    @Column(nullable = false)
    private int shippingFeeKrw;

    /**
     * 실제 청구할 금액.
     *
     * 주문을 만든 시점의 값이다. 이후 가격이나 프로모션이 바뀌어도 이 값은 그대로
     * 둔다. 결제한 금액과 청구한 금액이 달라지면 대조할 근거가 사라진다.
     */
    @Column(nullable = false)
    private int paymentAmountKrw;

    /** 결제 대행사가 준 결제 식별값. */
    @Column(length = 200)
    private String paymentKey;

    @Column(length = 30)
    private String paymentMethod;

    /** 이때까지 결제되지 않으면 만료된다. */
    private Instant paymentExpiresAt;

    /** 결제가 확인된 시각. */
    private Instant paidAt;

    /** 관리자가 취소하며 남긴 사유. */
    @Column(length = 300)
    private String cancelReason;

    @Column(length = 50)
    private String trackingCompany;

    @Column(length = 50)
    private String trackingNumber;

    /**
     * 광고성 정보 수신 동의.
     *
     * 개인정보 수집·이용 동의와 나눠 받는다. 광고성 정보는 별도로 동의를 받아야
     * 하고, 굿즈를 사는 데 필요한 동의와 묶으면 사실상 강제가 된다.
     */
    @Column(nullable = false)
    private boolean marketingConsent;

    private Instant marketingConsentedAt;

    @Column(length = 30)
    private String marketingConsentVersion;

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
            String orderNumber,
            GoodsOrderPricing pricing,
            boolean marketingConsent,
            String marketingConsentVersion,
            int paymentWindowMinutes,
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
        fulfillment.orderNumber = orderNumber;
        fulfillment.status = GoodsOrderStatus.PAYMENT_PENDING;
        fulfillment.listPriceKrw = pricing.listPriceKrw();
        fulfillment.discountAmountKrw = pricing.discountAmountKrw();
        fulfillment.promotionName = pricing.promotionName();
        fulfillment.shippingFeeKrw = pricing.shippingFeeKrw();
        fulfillment.paymentAmountKrw = pricing.paymentAmountKrw();
        fulfillment.paymentExpiresAt =
                privacyConsentedAt.plus(paymentWindowMinutes, ChronoUnit.MINUTES);
        fulfillment.marketingConsent = marketingConsent;
        if (marketingConsent) {
            fulfillment.marketingConsentedAt = privacyConsentedAt;
            fulfillment.marketingConsentVersion = marketingConsentVersion;
        }
        // 보존 기간은 주문 시점부터 센다. 배송 표시를 놓친 건도 법정 기간만큼
        // 남아야 하고, 전자상거래법도 거래 시점을 기준으로 삼는다.
        fulfillment.contractDeleteAfter = privacyConsentedAt.plus(contractRetentionDays, ChronoUnit.DAYS);
        return fulfillment;
    }

    /**
     * 결제가 확인됐다고 표시한다.
     *
     * 이미 확인한 건은 아무것도 바꾸지 않는다. 결제 대행사 웹훅은 같은 건을 여러 번
     * 보낼 수 있고, 그때마다 알림이 나가거나 시각이 덮이면 안 된다.
     * 바뀌었는지를 돌려주어 부른 쪽이 알림을 한 번만 보내게 한다.
     */
    public boolean markPaid(Instant paidAt, String paymentKey, String paymentMethod) {
        if (this.paidAt != null) {
            return false;
        }
        this.paidAt = paidAt;
        this.paymentKey = paymentKey;
        this.paymentMethod = paymentMethod;
        this.status = GoodsOrderStatus.PAYMENT_COMPLETED;
        return true;
    }

    /** 결제 대기 시간이 지났는지. */
    public boolean isPaymentExpired(Instant now) {
        return status == GoodsOrderStatus.PAYMENT_PENDING
                && paymentExpiresAt != null
                && !paymentExpiresAt.isAfter(now);
    }

    /**
     * 상태를 옮긴다.
     *
     * 이력은 부른 쪽이 남긴다. 여기서 함께 만들면 엔티티가 저장소를 알아야 한다.
     */
    public void changeStatus(GoodsOrderStatus next) {
        this.status = next;
    }

    public void cancel(GoodsOrderStatus next, String reason) {
        this.status = next;
        this.cancelReason = reason;
    }

    public void registerTracking(String company, String number) {
        this.trackingCompany = company;
        this.trackingNumber = number;
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
