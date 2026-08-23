package com.pawever.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 요청 스레드에서 떼어 낼 일을 위한 설정.
 *
 * 지금은 굿즈 신청 알림 하나가 쓴다. 커밋이 끝난 뒤에 텔레그램으로 보내는데,
 * 이것을 요청 스레드에서 그대로 하면 텔레그램이 느린 만큼 신청자가 결과
 * 화면을 늦게 본다. 연결과 읽기에 각각 5초를 주었으니 최악에는 10초다.
 *
 * 알림이 늦는 것과 접수가 늦는 것은 다른 문제다. 늦어도 되는 쪽만 늦춘다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
