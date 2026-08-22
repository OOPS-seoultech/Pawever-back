package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import com.pawever.backend.goodssurvey.entity.GoodsOrderPricing;
import com.pawever.backend.goodssurvey.entity.GoodsOrderSequence;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatusChange;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.repository.GoodsOrderSequenceRepository;
import com.pawever.backend.goodssurvey.repository.GoodsOrderStatusChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 주문번호를 매기고, 금액을 정하고, 상태가 바뀐 것을 남긴다.
 *
 * 금액은 여기서만 정한다. 화면이 보낸 값을 그대로 쓰면 요청을 고쳐 1원짜리
 * 주문을 만들 수 있다.
 */
@Service
@RequiredArgsConstructor
public class GoodsOrderService {

    /** 주문번호를 세는 기준 시간대. 연도가 바뀌는 시점이 한국 자정이어야 한다. */
    private static final ZoneId ORDER_NUMBER_ZONE = ZoneId.of("Asia/Seoul");

    private final GoodsOrderSequenceRepository sequenceRepository;
    private final GoodsOrderStatusChangeRepository statusChangeRepository;
    private final GoodsSurveyProperties properties;
    private final Clock clock;

    /**
     * 다음 주문번호를 꺼낸다.
     *
     * 행을 잠그고 올린다. 잠그지 않으면 동시에 신청한 두 사람이 같은 번호를 받고
     * 한쪽은 UNIQUE 에 걸려 저장에 실패한다.
     */
    @Transactional
    public String issueOrderNumber() {
        int year = clock.instant().atZone(ORDER_NUMBER_ZONE).getYear();
        GoodsOrderSequence sequence = sequenceRepository.findByYearForUpdate(year)
                .orElseGet(() -> sequenceRepository.save(GoodsOrderSequence.startOf(year)));
        return sequence.issue();
    }

    /**
     * 이 신청에 적용할 금액.
     *
     * 설문에 답하고 온 사람만 깎아 준다. 건너뛴 사람은 정상가다. 답하는 수고와
     * 값의 차이가 이 서비스가 설문을 받는 이유다.
     */
    public GoodsOrderPricing priceFor(boolean surveyParticipant) {
        if (!surveyParticipant) {
            return GoodsOrderPricing.listPrice(
                    properties.getListPriceKrw(), properties.getShippingFeeKrw());
        }
        return GoodsOrderPricing.discounted(
                properties.getListPriceKrw(),
                properties.getSurveyDiscountKrw(),
                properties.getSurveyPromotionName(),
                properties.getShippingFeeKrw()
        );
    }

    /** 주문이 만들어졌다는 첫 기록. 이전 상태가 없다. */
    @Transactional
    public void recordCreated(GoodsSurveyFulfillment fulfillment) {
        statusChangeRepository.save(GoodsOrderStatusChange.bySystem(
                fulfillment.getResponseId(),
                null,
                fulfillment.getStatus(),
                clock.instant(),
                "신청 접수"
        ));
    }

    /** 시스템이 상태를 바꿨다는 기록. 결제 승인과 만료가 여기로 온다. */
    @Transactional
    public void recordSystemChange(
            String responseId,
            GoodsOrderStatus from,
            GoodsOrderStatus to,
            String memo
    ) {
        statusChangeRepository.save(GoodsOrderStatusChange.bySystem(
                responseId,
                from,
                to,
                clock.instant(),
                memo
        ));
    }

    /** 사람이 상태를 바꿨다는 기록. */
    @Transactional
    public void recordManualChange(
            String responseId,
            GoodsOrderStatus from,
            GoodsOrderStatus to,
            String changedBy,
            String memo
    ) {
        statusChangeRepository.save(GoodsOrderStatusChange.of(
                responseId,
                from,
                to,
                clock.instant(),
                changedBy,
                memo
        ));
    }

    public Instant now() {
        return clock.instant();
    }
}
