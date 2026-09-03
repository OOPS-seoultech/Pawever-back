package com.pawever.backend.notification.telegram;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 텔레그램으로 실제로 보내는 자리.
 *
 * 대표님 예시는 보낸 결과를 확인하지 않는다. 확인하지 않으면 토큰을 새로
 * 발급했거나 봇이 채널에서 빠진 뒤에도 알림이 조용히 멈춘다. 여기서는
 * 실패를 알아채되, 그 실패가 접수까지 끌고 가지 않는 것을 본다.
 */
class TelegramClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private TelegramProperties properties;
    private TelegramClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        properties = new TelegramProperties();
        properties.setBotToken("test-token");
        properties.setChatId("-1001234567890");
        client = new TelegramClient(restTemplate, properties);
    }

    @Test
    void 토큰과_채널을_담아_HTML_로_보낸다() {
        server.expect(requestTo("https://api.telegram.org/bottest-token/sendMessage"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.chat_id").value("-1001234567890"))
                .andExpect(jsonPath("$.parse_mode").value("HTML"))
                .andExpect(jsonPath("$.text").value("안녕"))
                .andRespond(withSuccess("{\"ok\":true}", org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(client.sendHtml("안녕")).isTrue();
        server.verify();
    }

    @Test
    void 설정이_없으면_아예_보내지_않는다() {
        // 로컬과 테스트에서는 값이 없는 것이 정상이다. 없다고 접수를 막으면
        // 알림 설정이 접수의 전제가 된다.
        properties.setBotToken("");

        assertThat(client.sendHtml("안녕")).isFalse();
        server.verify();
    }

    @Test
    void 실패_로그에_봇_토큰이_남지_않는다() {
        // 봇 토큰은 그 자체가 봇이다. 가진 사람은 우리 채널에 글을 쓰고 봇이
        // 받는 메시지를 읽는다.
        //
        // 그런데 요청 주소에 토큰이 박혀 있고, RestTemplate 예외의 문구에는
        // 그 주소가 통째로 들어간다. "주소는 남기지 않는다"고 적어 두고
        // e.getMessage() 를 그대로 찍고 있었다 — 2026-09-03 운영 로그에
        // 토큰이 찍혔고, 그 로그를 읽을 수 있는 사람은 모두 봇을 쓸 수 있게
        // 됐다.
        properties.setBotToken("123456789:AA-secret-value");
        Logger logger = (Logger) LoggerFactory.getLogger(TelegramClient.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        logger.setLevel(Level.DEBUG);

        server.expect(requestTo("https://api.telegram.org/bot123456789:AA-secret-value/sendMessage"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"ok\":false,\"error_code\":404,\"description\":\"Not Found\"}")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(client.sendHtml("안녕")).isFalse();

        String written = logs.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + " " + b);
        logger.detachAppender(logs);

        assertThat(written).doesNotContain("AA-secret-value");
        // 원인은 남아야 한다. 까닭 없는 실패 로그는 없는 것과 같다.
        assertThat(written).contains("404");
        assertThat(written).contains("Not Found");
    }

    @Test
    void 텔레그램이_거절해도_던지지_않는다() {
        // 이스케이프를 놓쳤거나 봇이 채널에서 빠지면 400 이 온다. 그때
        // 예외를 밖으로 내보내면 알림 실패가 접수 실패처럼 번진다.
        server.expect(requestTo("https://api.telegram.org/bottest-token/sendMessage"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.sendHtml("안녕")).isFalse();
        server.verify();
    }
}
