package com.pawever.backend.notification.telegram;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/** 제작 검수 알림만 새 운영 봇으로 보낸다. 알림 실패가 제작 흐름을 막아서는 안 된다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramOpsClient {
  private final RestTemplate restTemplate;
  private final TelegramOpsProperties properties;

  public boolean sendHtml(String text) {
    if (!properties.isConfigured()) {
      log.debug("검수용 텔레그램 설정이 없어 알림을 보내지 않는다");
      return false;
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> body =
        Map.of(
            "chat_id", properties.getChatId(),
            "text", text,
            "parse_mode", "HTML",
            "disable_web_page_preview", true);
    try {
      restTemplate.postForEntity(
          properties.getBaseUrl() + "/bot" + properties.getBotToken() + "/sendMessage",
          new HttpEntity<>(body, headers),
          String.class);
      return true;
    } catch (RestClientException e) {
      log.error("검수용 텔레그램 알림 전송 실패: {}", describe(e));
      return false;
    }
  }

  private String describe(RestClientException e) {
    if (e instanceof RestClientResponseException answered) {
      return answered.getStatusCode() + " " + answered.getResponseBodyAsString();
    }
    return e.getClass().getSimpleName();
  }
}
