package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.global.security.HmacHasher;
import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
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
    private final GoodsOrderService orderService;
    private final HmacHasher hmacHasher;
    private final GoodsSurveyUnsubscribeToken unsubscribeToken;
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
     * 결제를 사람이 확인했다고 표시한다.
     *
     * 평상시 결제는 대행사 승인으로 자동 처리된다. 이 통로는 대행사 쪽에서
     * 승인이 났는데 웹훅이 오지 않는 것처럼, 자동으로 풀리지 않는 건을 손으로
     * 맞추기 위한 것이다. 이미 확인된 건은 아무것도 바꾸지 않는다.
     */
    @Transactional
    public Instant markPaid(String responseId) {
        GoodsSurveyFulfillment fulfillment = fulfillmentRepository.findByResponseId(responseId)
                .orElseThrow(() -> new CustomException(ErrorCode.SURVEY_RESPONSE_NOT_FOUND));

        GoodsOrderStatus before = fulfillment.getStatus();
        boolean changed = fulfillment.markPaid(goodsSurveyClock.instant(), null, "MANUAL");
        if (changed) {
            orderService.recordSystemChange(
                    responseId,
                    before,
                    fulfillment.getStatus(),
                    "결제 수동 확인"
            );
        }
        return fulfillment.getPaidAt();
    }

    /**
     * 메일에 실을 수신거부 링크의 값을 만든다.
     *
     * 보내는 쪽은 주소를 알고 있으므로 여기서 번호를 찾아 서명해 준다.
     * 링크에는 주소가 아니라 이 값을 싣는다.
     */
    @Transactional(readOnly = true)
    public String issueUnsubscribeToken(String email) {
        String emailHash = hmacHasher.hash("notice:" + normalizeEmail(email));
        return noticeSubscriptionRepository.findByEmailHash(emailHash)
                .map(subscription -> unsubscribeToken.issue(subscription.getId()))
                .orElseThrow(() -> new CustomException(ErrorCode.SURVEY_RESPONSE_NOT_FOUND));
    }

    /**
     * 링크를 눌러 스스로 수신거부한다.
     *
     * 사람 손을 거치지 않는다. 문의를 받아 처리하면 그 사이에 발송이 나갈 수
     * 있는데, 요구서는 즉시 제외하라고 한다.
     *
     * 값이 맞지 않아도 조용히 넘어간다. 링크가 이미 처리됐는지, 우리가 만든
     * 값이 맞는지를 알려 주면 그것으로 하나씩 넣어 볼 수 있게 된다.
     */
    @Transactional
    public void unsubscribeByToken(String token) {
        unsubscribeToken.verify(token)
                .flatMap(noticeSubscriptionRepository::findById)
                .ifPresent(subscription -> subscription.unsubscribe(goodsSurveyClock.instant()));
    }

    /**
     * 문의를 받아 사람이 수신거부를 처리한다.
     *
     * 링크를 누르지 못하는 경우를 위해 남겨 둔다. 내부 토큰으로 막혀 있다.
     * 표시만 남기고, 주소는 다음 파기 작업이 돌 때 지워진다. 없는 주소를
     * 물어도 접수한 것처럼 답한다. 어떤 주소가 등록돼 있는지 확인하는
     * 통로로 쓰이면 안 된다.
     */
    @Transactional
    public void unsubscribeNotice(String email) {
        String normalized = normalizeEmail(email);
        if (normalized.isBlank()) {
            return;
        }

        String emailHash = hmacHasher.hash("notice:" + normalized);
        noticeSubscriptionRepository.findByEmailHash(emailHash)
                .ifPresent(subscription -> subscription.unsubscribe(goodsSurveyClock.instant()));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
