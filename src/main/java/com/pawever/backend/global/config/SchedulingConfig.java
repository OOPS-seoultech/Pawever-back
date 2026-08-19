package com.pawever.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 작업 활성화.
 *
 * 보유 기간이 지난 개인정보를 지우는 작업이 여기에 걸려 있다. 이 설정이 빠지면
 * 방침에 적어 둔 파기 기간이 지켜지지 않는데, 아무 오류도 나지 않아 알아채기
 * 어렵다. 테스트(SchedulingConfigTest)로 켜져 있는지 고정해 둔다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
