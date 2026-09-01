package com.pawever.backend.notification.sms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 알리고로 실제로 보내는 자리.
 *
 * 여기서 중요한 것은 "안 나간 문자를 나갔다고 세지 않는 것"이다. 알리고는
 * 키가 틀려도 발신번호가 미등록이어도 잔액이 없어도 HTTP 200 을 준다.
 * 상태 코드만 보면 전부 성공으로 보인다.
 */
class SmsClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private SmsProperties properties;
    private SmsClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        properties = new SmsProperties();
        properties.setApiKey("test-key");
        properties.setUserId("pawever");
        properties.setSender("0212345678");
        properties.getBank().setName("국민은행");
        properties.getBank().setAccount("123456-78-901234");
        properties.getBank().setHolder("포에버");
        client = new SmsClient(restTemplate, properties);
    }

    @Test
    void 키와_발신번호를_담아_LMS_로_보낸다() {
        // 계좌·금액·기한을 담으면 단문 한도를 넘는다. SMS 로 나가면 알리고가
        // 뒤를 잘라 계좌번호가 끊긴 문자가 간다.
        server.expect(requestTo("https://apis.aligo.in/send/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("key=test-key")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("msg_type=LMS")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("testmode_yn=N")))
                .andRespond(withSuccess(
                        "{\"result_code\":1,\"message\":\"success\",\"msg_id\":\"123\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.sendLms("01012345678", "제목", "본문")).isTrue();
        server.verify();
    }

    @Test
    void 하이픈을_뗀_번호로_보낸다() {
        // 신청 화면 자리표시자가 010-0000-0000 이라 대부분 하이픈을 넣어 적는다.
        // 알리고는 하이픈 섞인 번호를 받아 주기도 하고 거절하기도 한다. 어긋나면
        // 실패도 성공도 아닌 채로 문자만 안 간다.
        server.expect(requestTo("https://apis.aligo.in/send/"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("receiver=01012345678")))
                .andRespond(withSuccess(
                        "{\"result_code\":1,\"message\":\"success\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.sendLms("010-1234-5678", "제목", "본문")).isTrue();
        server.verify();
    }

    @Test
    void 알리고가_200_으로_거절하면_실패로_센다() {
        // 이 한 줄이 이 클래스가 있는 이유다. 상태 코드만 보면 키가 틀린
        // 발송도 성공으로 세고, 아무도 문자가 멈춘 줄 모른다.
        server.expect(requestTo("https://apis.aligo.in/send/"))
                .andRespond(withSuccess(
                        "{\"result_code\":-101,\"message\":\"인증오류입니다.\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.sendLms("01012345678", "제목", "본문")).isFalse();
        server.verify();
    }

    @Test
    void 설정이_없으면_아예_보내지_않는다() {
        // 로컬과 테스트에서는 값이 없는 것이 정상이다. 없다고 접수를 막으면
        // 문자 설정이 접수의 전제가 된다.
        properties.setApiKey("");

        assertThat(client.sendLms("01012345678", "제목", "본문")).isFalse();
        server.verify();
    }

    @Test
    void 계좌가_비어_있으면_보내지_않는다() {
        // 계좌 없는 입금 안내는 안내가 아니다. 받는 사람은 무엇을 해야 할지
        // 모른 채 기한만 흘려보낸다.
        properties.getBank().setAccount("");

        assertThat(client.sendLms("01012345678", "제목", "본문")).isFalse();
        server.verify();
    }

    @Test
    void 연결이_끊겨도_던지지_않는다() {
        // 접수는 이미 커밋되었다. 여기서 터뜨리면 접수까지 실패한 것처럼 보인다.
        server.expect(requestTo("https://apis.aligo.in/send/"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(client.sendLms("01012345678", "제목", "본문")).isFalse();
        server.verify();
    }
}
