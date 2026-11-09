package com.pawever.backend.notification.telegram;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.core.io.ByteArrayResource;
import com.pawever.backend.workflow.ModelReviewMedia;
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

  public boolean sendAlbum(ModelReviewMedia.Album album) {
    if (!properties.isConfigured() || album.photos().isEmpty()) return false;
    var body = new LinkedMultiValueMap<String, Object>();
    body.add("chat_id", properties.getChatId());
    var media = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < Math.min(9, album.photos().size()); i++) {
      var photo = album.photos().get(i);
      String name = "photo" + i, fileName = name + ".jpg";
      var bytes = new ByteArrayResource(photo.bytes()) { @Override public String getFilename() { return fileName; } };
      var imageHeaders = new HttpHeaders(); imageHeaders.setContentType(MediaType.IMAGE_JPEG);
      body.add(name, new HttpEntity<>(bytes, imageHeaders));
      media.add(Map.of("type", "photo", "media", "attach://" + name,
          "caption", (i == 0 ? album.title() + "\n" : "") + photo.caption()));
    }
    try {
      String method;
      if (media.size() == 1) {
        method = "sendPhoto";
        body.add("photo", "attach://photo0"); body.add("caption", media.get(0).get("caption"));
      } else {
        method = "sendMediaGroup";
        body.add("media", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(media));
      }
      var headers = new HttpHeaders(); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
      restTemplate.postForEntity(properties.getBaseUrl() + "/bot" + properties.getBotToken() + "/" + method,
          new HttpEntity<>(body, headers), String.class);
      return true;
    } catch (RestClientException | java.io.IOException error) {
      log.error("검수 사진 알림 전송 실패: {}", error.getClass().getSimpleName());
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
