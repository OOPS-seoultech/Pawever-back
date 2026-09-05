package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyNoticeSubscription;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhoto;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponse;
import com.pawever.backend.goodssurvey.repository.GoodsOrderStatusChangeRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyNoticeSubscriptionRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyPhotoRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyResponseRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyStoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 보유 기간이 지난 설문·굿즈 개인정보를 파기한다.
 *
 * 방침에 적어 둔 기간이 셋이고, 저마다 세는 기준이 다르다.
 *
 * <ul>
 *   <li>배송 정보와 제작용 사진 — 배송 완료 시점부터 {@code personalDataRetentionDays}</li>
 *   <li>안내 이메일 — 동의 시점부터 {@code noticeRetentionDays}, 수신거부하면 즉시</li>
 *   <li>설문 응답·사연·공개 동의 사진 — 수집 시점부터 {@code surveyRetentionDays}</li>
 * </ul>
 *
 * 파일을 먼저 지우고 행을 지운다. 순서를 뒤집으면 지우다 실패했을 때 어느
 * 파일을 지워야 하는지 알 방법이 없어져 저장소에 영영 남는다. 반대 순서는
 * 다음 회차에 같은 행이 다시 잡혀 이어서 지운다.
 */
@Service
@RequiredArgsConstructor
public class GoodsSurveyRetentionService {

    /**
     * 한 회차에 처리할 최대 건수.
     *
     * 파일 삭제는 저장소 왕복이라 한 건씩 시간이 든다. 밀린 물량이 많아도
     * 한 번에 다 붙잡지 않고 나눠서 처리한다. 매일 도니 며칠이면 따라잡는다.
     */
    private static final int BATCH_LIMIT = 200;

    private final GoodsSurveyFulfillmentRepository fulfillmentRepository;
    private final GoodsSurveyNoticeSubscriptionRepository noticeSubscriptionRepository;
    private final GoodsSurveyResponseRepository responseRepository;
    private final GoodsSurveyStoryRepository storyRepository;
    private final GoodsSurveyPhotoRepository photoRepository;
    private final GoodsSurveyPhotoStorage photoStorage;
    private final GoodsOrderStatusChangeRepository statusChangeRepository;
    private final GoodsSurveyProperties properties;
    private final GoodsOrderService orderService;

    /**
     * 배송이 끝나고 보유 기간이 지난 제작용 사진과 상세주소.
     *
     * 신청 정보 전체를 지우지는 않는다. 유료 판매의 주문·결제·공급 기록은
     * 전자상거래법이 5년 보존하도록 해, 그쪽은 {@link #purgeExpiredContracts}가 맡는다.
     */
    @Transactional
    public int purgeDeliveredFulfillments(Instant now) {
        List<GoodsSurveyFulfillment> targets =
                fulfillmentRepository.findByDeleteAfterNotNullAndDeleteAfterLessThanEqual(
                        now,
                        Limit.of(BATCH_LIMIT)
                );

        for (GoodsSurveyFulfillment fulfillment : targets) {
            deletePhotos(
                    photoRepository.findByResponseIdAndPublicationAgreedFalse(fulfillment.getResponseId())
            );
            fulfillment.stripDeliveryDetails();
        }
        return targets.size();
    }

    /**
     * 결제를 기다리다 시간이 지난 주문.
     *
     * 계약이 성립하지 않았으므로 보관할 근거가 없다. 사진까지 그 자리에서
     * 지운다. 다시 사려면 새 주문으로 처음부터 신청한다.
     */
    @Transactional
    public int expireUnpaidOrders(Instant now) {
        List<GoodsSurveyFulfillment> targets =
                fulfillmentRepository.findByStatusAndPaymentExpiresAtLessThanEqual(
                        GoodsOrderStatus.PAYMENT_PENDING,
                        now,
                        Limit.of(BATCH_LIMIT)
                );

        for (GoodsSurveyFulfillment fulfillment : targets) {
            discardVoidOrderData(fulfillment);
            fulfillment.changeStatus(GoodsOrderStatus.PAYMENT_EXPIRED);
            orderService.recordSystemChange(
                    fulfillment.getResponseId(),
                    GoodsOrderStatus.PAYMENT_PENDING,
                    GoodsOrderStatus.PAYMENT_EXPIRED,
                    "결제 대기 시간 경과"
            );
        }
        return targets.size();
    }

    /**
     * 계약이 성립하지 않았거나 되돌려진 주문의 사진과 상세주소를 그 자리에서
     * 지운다. 행은 남긴다 — 무엇이 왜 만료·취소됐는지 관리자가 볼 수 있어야 한다.
     *
     * 시간이 지나 저절로 만료된 건({@link #expireUnpaidOrders})과, 관리자가
     * 손으로 만료·실패·취소로 옮긴 건이 같은 자리에서 같은 일을 한다. 두 곳에
     * 따로 적으면 한쪽만 고쳐진다.
     *
     * 취소 처리 실패는 부르지 않는다. 돈은 받아 둔 채 환불에 실패한 상태라
     * 사람이 정리하기 전까지 그 물건은 이 사람 몫이다.
     */
    @Transactional
    public void discardVoidOrderData(GoodsSurveyFulfillment fulfillment) {
        deletePhotos(photoRepository.findByResponseId(fulfillment.getResponseId()));
        fulfillment.stripDeliveryDetails();
    }

    /** 법정 보존 기간까지 지난 계약 기록. */
    @Transactional
    public int purgeExpiredContracts(Instant now) {
        List<GoodsSurveyFulfillment> targets =
                fulfillmentRepository.findByContractDeleteAfterNotNullAndContractDeleteAfterLessThanEqual(
                        now,
                        Limit.of(BATCH_LIMIT)
                );

        for (GoodsSurveyFulfillment fulfillment : targets) {
            String responseId = fulfillment.getResponseId();
            // 사진은 앞 단계에서 이미 지웠지만, 배송 표시 없이 여기까지 온 건이
            // 있을 수 있어 한 번 더 훑는다.
            deletePhotos(photoRepository.findByResponseId(responseId));
            fulfillmentRepository.delete(fulfillment);
            // 계약 기록 때문에 남겨 뒀던 응답과 사연도 이제 지운다.
            // 설문 보유 기간(2년)은 이 시점이면 이미 한참 지났다.
            storyRepository.findByResponseId(responseId).ifPresent(storyRepository::delete);
            statusChangeRepository.deleteByResponseId(responseId);
            responseRepository.findById(responseId).ifPresent(responseRepository::delete);
        }
        return targets.size();
    }

    /** 보유 기간이 지났거나 수신을 거부한 안내 이메일. */
    @Transactional
    public int purgeNoticeSubscriptions(Instant now) {
        List<GoodsSurveyNoticeSubscription> targets =
                noticeSubscriptionRepository.findPurgeTargets(now, Limit.of(BATCH_LIMIT));
        noticeSubscriptionRepository.deleteAll(targets);
        return targets.size();
    }

    /**
     * 보유 기간이 지난 설문 응답 일체.
     *
     * 응답에 딸린 사연·사진·신청 정보를 함께 지운다. 배송을 표시하지 않아
     * 앞 단계에서 걸리지 않은 신청 정보도 여기서 정리된다. 고지한 기간 중
     * 가장 긴 것이 설문의 2년이라, 그보다 오래 남는 것이 있어선 안 된다.
     */
    @Transactional
    public int purgeExpiredSurveys(Instant now) {
        LocalDateTime threshold = LocalDateTime.ofInstant(
                now.minus(properties.getSurveyRetentionDays(), ChronoUnit.DAYS),
                ZoneOffset.UTC
        );
        List<GoodsSurveyResponse> targets =
                responseRepository.findByCreatedAtLessThanEqual(threshold, Limit.of(BATCH_LIMIT));

        int purged = 0;
        for (GoodsSurveyResponse response : targets) {
            String responseId = response.getId();
            GoodsSurveyFulfillment fulfillment =
                    fulfillmentRepository.findByResponseId(responseId).orElse(null);

            // 법정 보존 기간이 남은 계약 기록은 건드리지 않는다. 응답을 지우면
            // 그 기록이 어느 주문의 것인지 잃으므로 응답도 함께 남긴다.
            // contractDeleteAfter 가 지나면 purgeExpiredContracts 가 둘 다 정리한다.
            if (fulfillment != null && fulfillment.isContractRetained(now)) {
                continue;
            }

            deletePhotos(photoRepository.findByResponseId(responseId));
            storyRepository.findByResponseId(responseId).ifPresent(storyRepository::delete);
            if (fulfillment != null) {
                fulfillmentRepository.delete(fulfillment);
            }
            responseRepository.delete(response);
            purged++;
        }
        return purged;
    }

    private void deletePhotos(List<GoodsSurveyPhoto> photos) {
        for (GoodsSurveyPhoto photo : photos) {
            photoStorage.delete(photo.getObjectKey());
            photoRepository.delete(photo);
        }
    }
}
