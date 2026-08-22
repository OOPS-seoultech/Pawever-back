package com.pawever.backend.payment.service;

import com.pawever.backend.goodssurvey.service.GoodsSurveyRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 결제되지 않은 주문을 자주 걷어낸다.
 *
 * 파기 작업에도 같은 일이 들어 있지만 그쪽은 하루 한 번 새벽에 돈다. 결제
 * 대기는 30분짜리인데 하루를 기다리면, 그 사이 주문은 결제 대기로 남아
 * 관리자 목록을 채우고 사진도 계속 저장돼 있다. 계약이 성립하지 않은 건이다.
 *
 * 새벽 작업 쪽은 그대로 둔다. 이 스케줄러가 멈춰 있어도 하루에 한 번은
 * 걷히도록 남겨 둔 그물이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoodsPaymentExpiryScheduler {

    private final GoodsSurveyRetentionService retentionService;
    private final Clock goodsSurveyClock;

    @Scheduled(cron = "${survey.goods.payment-expiry-cron}", zone = "Asia/Seoul")
    public void expire() {
        try {
            int expired = retentionService.expireUnpaidOrders(goodsSurveyClock.instant());
            if (expired > 0) {
                log.info("결제 대기 만료 {}건", expired);
            }
        } catch (RuntimeException exception) {
            // 여기서 예외가 올라가면 스케줄러가 멈춘다. 다음 회차에 다시 잡힌다.
            log.error("결제 대기 만료 처리 실패", exception);
        }
    }
}
