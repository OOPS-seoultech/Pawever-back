package com.pawever.backend.notification.telegram;

import com.pawever.backend.global.event.ApiContractBreachEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 요청을 읽지도 못하고 거절하고 있다는 사실을 팀 채널로 알린다.
 *
 * 입금 안내가 못 나갔다는 알림과 같은 자리에 둔다. 저쪽은 "이 사람은 낼 방법이
 * 없다"이고 이쪽은 "아무도 신청할 수 없다"이다.
 *
 * 같은 경로는 한동안 한 번만 알린다. 이런 고장은 한 건으로 끝나지 않고 들어오는
 * 모든 요청에서 터지므로, 그대로 보내면 채널이 같은 문장으로 덮인다. 덮인 채널은
 * 아무도 읽지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiContractBreachTelegramListener {

    static final Duration QUIET_PERIOD = Duration.ofMinutes(30);

    private final TelegramClient telegramClient;
    private final Clock clock;
    /** 경로별로 마지막에 알린 시각. 인스턴스가 하나라 메모리로 충분하다. */
    private final Map<String, Instant> lastNotifiedByPath = new ConcurrentHashMap<>();

    @Async
    @EventListener
    public void onApiContractBreach(ApiContractBreachEvent event) {
        if (!shouldNotify(event.path())) {
            return;
        }
        try {
            telegramClient.sendHtml(TelegramMessage.apiContractBreach(
                    event.path(),
                    (int) QUIET_PERIOD.toMinutes()
            ));
        } catch (RuntimeException e) {
            log.error("요청 거절 알림을 보내지 못했다: {}", event.path(), e);
        }
    }

    /**
     * 지금 알릴 차례인지.
     *
     * 판단과 기록을 한 번에 한다. 읽고 나서 쓰면 동시에 들어온 두 건이 모두
     * 통과해 같은 알림이 두 번 나간다.
     *
     * 돌려받은 시각으로 판단하지 않는다. 시계가 멈춰 있으면(테스트의 고정 시계)
     * 방금 적어 둔 값과 지금 시각이 같아, 조용히 넘겨야 할 때도 알린 것으로 본다.
     */
    private boolean shouldNotify(String path) {
        Instant now = clock.instant();
        AtomicBoolean notify = new AtomicBoolean(false);
        lastNotifiedByPath.compute(path, (key, last) -> {
            if (last != null && last.plus(QUIET_PERIOD).isAfter(now)) {
                return last;
            }
            notify.set(true);
            return now;
        });
        return notify.get();
    }
}
