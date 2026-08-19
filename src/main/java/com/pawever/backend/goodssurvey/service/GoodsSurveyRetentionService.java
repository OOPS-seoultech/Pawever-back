package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyNoticeSubscription;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhoto;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponse;
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
    private final GoodsSurveyProperties properties;

    /** 배송이 끝나고 보유 기간이 지난 신청 정보와 제작용 사진. */
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
            fulfillmentRepository.delete(fulfillment);
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

        for (GoodsSurveyResponse response : targets) {
            String responseId = response.getId();
            deletePhotos(photoRepository.findByResponseId(responseId));
            storyRepository.findByResponseId(responseId).ifPresent(storyRepository::delete);
            fulfillmentRepository.findByResponseId(responseId).ifPresent(fulfillmentRepository::delete);
            responseRepository.delete(response);
        }
        return targets.size();
    }

    private void deletePhotos(List<GoodsSurveyPhoto> photos) {
        for (GoodsSurveyPhoto photo : photos) {
            photoStorage.delete(photo.getObjectKey());
            photoRepository.delete(photo);
        }
    }
}
