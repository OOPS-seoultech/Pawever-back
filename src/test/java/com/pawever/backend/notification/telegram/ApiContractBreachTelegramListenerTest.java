package com.pawever.backend.notification.telegram;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.pawever.backend.global.event.ApiContractBreachEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class ApiContractBreachTelegramListenerTest {

    private static final String PATH = "POST /api/public/goods-survey/responses/1/application";

    @Test
    void 요청을_읽지_못하면_경로만_실어_팀_채널에_알린다() {
        TelegramClient client = mock(TelegramClient.class);
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        ApiContractBreachTelegramListener listener =
                new ApiContractBreachTelegramListener(client, Clock.fixed(now, ZoneOffset.UTC));

        listener.onApiContractBreach(new ApiContractBreachEvent(PATH));

        verify(client).sendHtml(TelegramMessage.apiContractBreach(
                PATH,
                (int) ApiContractBreachTelegramListener.QUIET_PERIOD.toMinutes()
        ));
    }

    @Test
    void 같은_경로가_계속_막혀도_조용한_동안은_한_번만_알린다() {
        // 이런 고장은 들어오는 모든 요청에서 터진다. 그대로 보내면 채널이 같은
        // 문장으로 덮이고, 덮인 채널은 아무도 읽지 않는다.
        TelegramClient client = mock(TelegramClient.class);
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        ApiContractBreachTelegramListener listener =
                new ApiContractBreachTelegramListener(client, Clock.fixed(now, ZoneOffset.UTC));

        listener.onApiContractBreach(new ApiContractBreachEvent(PATH));
        listener.onApiContractBreach(new ApiContractBreachEvent(PATH));
        listener.onApiContractBreach(new ApiContractBreachEvent(PATH));

        verify(client, times(1)).sendHtml(org.mockito.ArgumentMatchers.anyString());
        verifyNoMoreInteractions(client);
    }

    @Test
    void 조용한_기간이_지나면_다시_알린다() {
        TelegramClient client = mock(TelegramClient.class);
        Instant first = Instant.parse("2026-09-22T12:00:00Z");
        Instant later = first.plus(ApiContractBreachTelegramListener.QUIET_PERIOD).plusSeconds(1);
        MutableClock clock = new MutableClock(first);
        ApiContractBreachTelegramListener listener =
                new ApiContractBreachTelegramListener(client, clock);

        listener.onApiContractBreach(new ApiContractBreachEvent(PATH));
        clock.moveTo(later);
        listener.onApiContractBreach(new ApiContractBreachEvent(PATH));

        verify(client, times(2)).sendHtml(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 경로가_다르면_각각_알린다() {
        TelegramClient client = mock(TelegramClient.class);
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        ApiContractBreachTelegramListener listener =
                new ApiContractBreachTelegramListener(client, Clock.fixed(now, ZoneOffset.UTC));

        listener.onApiContractBreach(new ApiContractBreachEvent(PATH));
        listener.onApiContractBreach(new ApiContractBreachEvent("POST /api/public/goods-survey/responses"));

        verify(client, times(2)).sendHtml(org.mockito.ArgumentMatchers.anyString());
    }

    /** 시간이 흐르는 시계. Clock.fixed 로는 조용한 기간이 지나는 것을 볼 수 없다. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void moveTo(Instant next) {
            this.instant = next;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
