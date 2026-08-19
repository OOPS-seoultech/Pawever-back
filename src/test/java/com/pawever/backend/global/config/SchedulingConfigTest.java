package com.pawever.backend.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주기 작업이 켜져 있는지 고정한다.
 *
 * 이 설정이 빠지면 보유 기간이 지난 개인정보가 파기되지 않는데, 오류가 나지
 * 않아 한참 뒤에야 알아챈다. 방침에 적어 둔 기간을 코드가 지키는 근거다.
 */
class SchedulingConfigTest {

    @Test
    void 주기_작업이_켜져_있다() {
        assertThat(SchedulingConfig.class.getAnnotation(EnableScheduling.class)).isNotNull();
    }
}
