package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.global.security.HmacHasher;
import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyNoticeSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/**
 * 발송·수신거부처럼 사람이 처리한 사실을 기록하는 통로.
 *
 * 파기 작업은 여기서 찍힌 값을 보고 돈다. 배송 완료를 표시하지 않으면 배송
 * 정보의 보유 기간을 셀 기준일이 없어 계속 남는다.
 */
@Service
@RequiredArgsConstructor
public class GoodsSurveyFulfillmentOpsService {

    private final GoodsSurveyFulfillmentRepository fulfillmentRepository;
    private final GoodsSurveyNoticeSubscriptionRepository noticeSubscriptionRepository;
    private final GoodsSurveyProperties properties;
    private final HmacHasher hmacHasher;
    private final Clock goodsSurveyClock;

    /**
     * 굿즈를 발송했다고 표시한다.
     *
     * 이 시점부터 보유 기간을 센다. 이미 표시한 건은 그대로 둔다. 다시 찍으면
     * 파기 예정일이 뒤로 밀려 고지한 기간보다 오래 갖고 있게 된다.
     */
    @Transactional
    public Instant markDeliveryCompleted(String responseId) {
        GoodsSurveyFulfillment fulfillment = fulfillmentRepository.findByResponseId(responseId)
                .orElseThrow(() -> new CustomException(ErrorCode.SURVEY_RESPONSE_NOT_FOUND));

        if (fulfillment.getDeleteAfter() != null) {
            return fulfillment.getDeleteAfter();
        }

        fulfillment.markDeliveryCompleted(
                goodsSurveyClock.instant(),
                properties.getPersonalDataRetentionDays()
        );
        return fulfillment.getDeleteAfter();
    }

    /**
     * 안내 이메일 수신거부를 접수한다.
     *
     * 표시만 남기고, 주소는 다음 파기 작업이 돌 때 지워진다. 없는 주소를
     * 물어도 접수한 것처럼 답한다. 어떤 주소가 등록돼 있는지 확인하는
     * 통로로 쓰이면 안 된다.
     */
    @Transactional
    public void unsubscribeNotice(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return;
        }

        String emailHash = hmacHasher.hash("notice:" + normalized);
        noticeSubscriptionRepository.findByEmailHash(emailHash)
                .ifPresent(subscription -> subscription.unsubscribe(goodsSurveyClock.instant()));
    }
}
