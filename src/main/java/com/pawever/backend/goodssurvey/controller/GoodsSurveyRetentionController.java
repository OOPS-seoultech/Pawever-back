package com.pawever.backend.goodssurvey.controller;

import com.pawever.backend.goodssurvey.dto.UnsubscribeGoodsSurveyNoticeRequest;
import com.pawever.backend.goodssurvey.service.GoodsSurveyFulfillmentOpsService;
import com.pawever.backend.goodssurvey.service.GoodsSurveyInternalToken;
import com.pawever.backend.goodssurvey.service.GoodsSurveyRetentionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * 보유 기간 운영 통로.
 *
 * 파기는 매일 새벽에 저절로 돈다. 여기 있는 것은 그 작업이 볼 값을 사람이
 * 찍어 주는 통로다. 배송 완료를 표시해야 보유 기간을 셀 기준일이 생긴다.
 */
@RestController
@RequestMapping("/api/internal/goods-survey")
@RequiredArgsConstructor
public class GoodsSurveyRetentionController {

    private final GoodsSurveyFulfillmentOpsService opsService;
    private final GoodsSurveyRetentionService retentionService;
    private final GoodsSurveyInternalToken internalToken;
    private final Clock goodsSurveyClock;

    /** 굿즈를 발송했다고 표시한다. 응답의 deleteAfter 가 파기 예정일이다. */
    @PostMapping("/fulfillments/{responseId}/delivery-completed")
    public ResponseEntity<Map<String, Object>> markDeliveryCompleted(
            @RequestHeader(value = GoodsSurveyInternalToken.HEADER, required = false) String token,
            @PathVariable String responseId
    ) {
        internalToken.require(token);
        Instant deleteAfter = opsService.markDeliveryCompleted(responseId);
        return ResponseEntity.ok(Map.of(
                "responseId", responseId,
                "deleteAfter", deleteAfter.toString()
        ));
    }

    /** 계좌 입금을 확인했다고 표시한다. */
    @PostMapping("/fulfillments/{responseId}/paid")
    public ResponseEntity<Map<String, Object>> markPaid(
            @RequestHeader(value = GoodsSurveyInternalToken.HEADER, required = false) String token,
            @PathVariable String responseId
    ) {
        internalToken.require(token);
        Instant paidAt = opsService.markPaid(responseId);
        return ResponseEntity.ok(Map.of(
                "responseId", responseId,
                "paidAt", paidAt.toString()
        ));
    }

    /** 안내 이메일 수신거부를 접수한다. 등록되지 않은 주소도 같은 응답을 준다. */
    @PostMapping("/notice-subscriptions/unsubscribe")
    public ResponseEntity<Void> unsubscribeNotice(
            @RequestHeader(value = GoodsSurveyInternalToken.HEADER, required = false) String token,
            @Valid @RequestBody UnsubscribeGoodsSurveyNoticeRequest request
    ) {
        internalToken.require(token);
        opsService.unsubscribeNotice(request.email());
        return ResponseEntity.noContent().build();
    }

    /**
     * 파기를 지금 한 번 돌린다.
     *
     * 정기 작업을 기다리지 않고 확인할 때 쓴다. 도는 내용은 정기 작업과 같다.
     */
    @PostMapping("/retention/purge")
    public ResponseEntity<Map<String, Integer>> purgeNow(
            @RequestHeader(value = GoodsSurveyInternalToken.HEADER, required = false) String token
    ) {
        internalToken.require(token);
        Instant now = goodsSurveyClock.instant();
        return ResponseEntity.ok(Map.of(
                "fulfillments", retentionService.purgeDeliveredFulfillments(now),
                "noticeSubscriptions", retentionService.purgeNoticeSubscriptions(now),
                "surveys", retentionService.purgeExpiredSurveys(now),
                "contracts", retentionService.purgeExpiredContracts(now)
        ));
    }
}
