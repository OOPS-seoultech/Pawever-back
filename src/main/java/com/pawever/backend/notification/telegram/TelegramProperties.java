package com.pawever.backend.notification.telegram;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 텔레그램 알림 설정.
 *
 * 봇 토큰은 그 자체가 봇이다. 토큰을 가진 사람은 우리 채널에 글을 쓰고 봇이
 * 받는 메시지를 읽는다. 그래서 기본값을 두지 않는다 — 기본값을 두면 그 값이
 * 그대로 운영에 나간다.
 *
 * 실제로 토큰과 관리자 비밀번호가 카톡에 평문으로 오간 적이 있어 둘 다
 * 폐기하고 다시 발급했다. 이 값들은 서버 환경변수로만 들어온다.
 *
 * 채널이 비공개라 @이름 이 없다. chatId 는 -100 으로 시작하는 숫자이고,
 * 봇이 그 채널의 관리자로 들어가 있어야 글이 써진다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notification.telegram", ignoreUnknownFields = false)
public class TelegramProperties {

    /** BotFather 가 준 값. 서버 밖으로 나가면 안 된다. */
    private String botToken = "";

    /** 보낼 채널. 비공개 채널이라 -100 으로 시작하는 숫자다. */
    private String chatId = "";

    private String baseUrl = "https://api.telegram.org";

    /**
     * 설정이 없으면 보내지 않는다.
     *
     * 로컬과 테스트에서는 값이 없는 것이 정상이다. 없다고 접수를 막으면
     * 알림 설정이 접수의 전제가 된다.
     */
    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank()
                && chatId != null && !chatId.isBlank();
    }
}
