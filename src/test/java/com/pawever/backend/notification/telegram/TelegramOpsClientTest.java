package com.pawever.backend.notification.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class TelegramOpsClientTest {
  private MockRestServiceServer server;
  private TelegramOpsProperties properties;
  private TelegramOpsClient client;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.createServer(restTemplate);
    properties = new TelegramOpsProperties();
    properties.setBotToken("ops-token");
    properties.setChatId("-1009876543210");
    client = new TelegramOpsClient(restTemplate, properties);
  }

  @Test
  void 검수용_봇의_토큰과_방으로만_알림을_보낸다() {
    server.expect(requestTo("https://api.telegram.org/botops-token/sendMessage"))
        .andExpect(jsonPath("$.chat_id").value("-1009876543210"))
        .andExpect(jsonPath("$.text").value("검수 대기"))
        .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

    assertThat(client.sendHtml("검수 대기")).isTrue();
    server.verify();
  }

  @Test
  void 검수용_설정이_없으면_기존_알림방으로_대신_보내지_않는다() {
    properties.setChatId("");

    assertThat(client.sendHtml("검수 대기")).isFalse();
    server.verify();
  }

  @Test
  void 사진_아홉장을_운영방_앨범으로_보내며_서명URL을_노출하지_않는다() {
    var photos = java.util.stream.IntStream.range(0, 9)
        .mapToObj(i -> new com.pawever.backend.workflow.ModelReviewMedia.Photo("사진 " + i, new byte[]{1,2,3})).toList();
    server.expect(requestTo("https://api.telegram.org/botops-token/sendMediaGroup"))
        .andExpect(request -> {
          var mock = (org.springframework.mock.http.client.MockClientHttpRequest) request;
          String body = mock.getBodyAsString();
          assertThat(body).contains("attach://photo0", "attach://photo8", "-1009876543210");
          assertThat(body).doesNotContain("attach://photo9", "https://storage");
        }).andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
    assertThat(client.sendAlbum(new com.pawever.backend.workflow.ModelReviewMedia.Album("검수", photos))).isTrue();
    server.verify();
  }
}
