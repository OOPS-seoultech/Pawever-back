package com.pawever.backend.goodssurvey.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * 파기 작업을 매일 돌린다.
 *
 * 세 갈래를 따로 부른다. 한 갈래가 실패해도 나머지는 그날 처리되고, 실패한
 * 갈래는 대상이 그대로 남아 다음 회차에 다시 잡힌다. 한 트랜잭션으로 묶으면
 * 사진 하나를 못 지웠다는 이유로 그날 파기가 통째로 멈춘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoodsSurveyRetentionScheduler {

    private final GoodsSurveyRetentionService retentionService;
    private final Clock goodsSurveyClock;

    @Scheduled(cron = "${survey.goods.purge-cron}", zone = "Asia/Seoul")
    public void purge() {
        Instant now = goodsSurveyClock.instant();

        int expired = run("결제 만료", () -> retentionService.expireUnpaidOrders(now));
        int fulfillments = run("사진·상세주소", () -> retentionService.purgeDeliveredFulfillments(now));
        int subscriptions = run("안내 이메일", () -> retentionService.purgeNoticeSubscriptions(now));
        int surveys = run("설문 응답", () -> retentionService.purgeExpiredSurveys(now));
        int contracts = run("계약 기록", () -> retentionService.purgeExpiredContracts(now));
        int accessLogs = run("담당자 접속기록", () -> retentionService.purgeExpiredAccessLogs(now));

        if (expired + fulfillments + subscriptions + surveys + contracts + accessLogs > 0) {
            log.info(
                    "보유 기간 파기 완료: 결제 만료 {}건, 사진·상세주소 {}건, 안내 이메일 {}건,"
                            + " 설문 응답 {}건, 계약 기록 {}건, 접속기록 {}건",
                    expired,
                    fulfillments,
                    subscriptions,
                    surveys,
                    contracts,
                    accessLogs
            );
        }
    }

    private int run(String label, PurgeStep step) {
        try {
            return step.execute();
        } catch (Exception exception) {
            // 대상이 남아 있으니 다음 회차에 다시 잡힌다. 여기서 멈추면 안 된다.
            log.error("보유 기간 파기 실패: {}", label, exception);
            return 0;
        }
    }

    @FunctionalInterface
    private interface PurgeStep {
        int execute();
    }
}
