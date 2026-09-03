package com.pawever.backend.notification.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 텔레그램으로 문장 하나를 보낸다.
 *
 * 대표님이 준 예시는 fetch 결과를 확인하지 않는다. fetch 도 RestTemplate 도
 * 실패를 알려 주는 방식이 다를 뿐, 확인하지 않으면 결과는 같다 — 토큰을
 * 새로 발급했거나 봇이 채널에서 빠져도 알림이 조용히 멈춘다. 멈춘 줄 모르는
 * 알림은 없는 알림보다 나쁘다. 그래서 여기서는 반드시 로그를 남긴다.
 *
 * 반대로 실패를 밖으로 던지지는 않는다. 이 호출이 실패했다는 것은 알림이
 * 못 갔다는 뜻이지 신청이 잘못됐다는 뜻이 아니다. 접수는 이미 커밋되었다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramClient {

    private final RestTemplate restTemplate;
    private final TelegramProperties properties;

    /** @return 실제로 보냈으면 true. 설정이 없거나 실패하면 false. */
    public boolean sendHtml(String text) {
        if (!properties.isConfigured()) {
            log.debug("텔레그램 설정이 없어 알림을 보내지 않는다");
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "chat_id", properties.getChatId(),
                "text", text,
                "parse_mode", "HTML",
                // 링크 미리보기가 열리면 알림 한 건이 화면 한 장을 먹는다.
                "disable_web_page_preview", true
        );

        try {
            restTemplate.postForEntity(
                    properties.getBaseUrl() + "/bot" + properties.getBotToken() + "/sendMessage",
                    new HttpEntity<>(body, headers),
                    String.class
            );
            return true;
        } catch (RestClientException e) {
            log.error("텔레그램 알림 전송 실패: {}", describe(e));
            return false;
        }
    }

    /**
     * 실패한 까닭만 남긴다. 주소는 남기지 않는다.
     *
     * 주소에 봇 토큰이 박혀 있다. 그리고 {@code e.getMessage()} 에는 그 주소가
     * 통째로 들어간다 — 남기지 않으려던 것이 예외 문구를 타고 그대로 나갔다.
     * 실제로 운영 로그에 토큰이 찍혔고, 그 로그를 읽을 수 있는 사람은 모두
     * 봇을 쓸 수 있게 됐다.
     *
     * 답이 온 경우에는 상태와 본문만 남긴다. 텔레그램이 주는 본문에는 토큰이
     * 없고, 무엇이 잘못됐는지는 거기에 적혀 있다.
     */
    private String describe(RestClientException e) {
        if (e instanceof RestClientResponseException answered) {
            return answered.getStatusCode() + " " + answered.getResponseBodyAsString();
        }
        return e.getClass().getSimpleName();
    }
}
