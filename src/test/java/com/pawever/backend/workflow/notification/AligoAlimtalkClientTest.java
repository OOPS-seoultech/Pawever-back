package com.pawever.backend.workflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AligoAlimtalkClientTest {
  private MockRestServiceServer server;
  private ShipmentNotificationProperties properties;
  private AligoAlimtalkClient client;

  @BeforeEach
  void setUp() {
    var restTemplate = new RestTemplate();
    server = MockRestServiceServer.createServer(restTemplate);
    properties = new ShipmentNotificationProperties();
    properties.setEnabled(true);
    properties.setApiKey("api-key");
    properties.setUserId("pawever");
    properties.setSenderKey("sender-key");
    properties.setSender("0212345678");
    properties.setTemplateCode("shipment-template");
    properties.setSubject("상품 발송 안내");
    properties.setApprovedBody("#{보호자이름}님 #{반려견이름} 송장 #{송장번호}");
    properties.setApprovedBodySha256(
        ShipmentNotificationProperties.hash(properties.getApprovedBody()));
    properties.setSuccessResultCodes(List.of("0000"));
    client = new AligoAlimtalkClient(restTemplate, properties);
  }

  @Test
  void codeZeroMeansAcceptedNotRecipientSuccess() {
    server
        .expect(requestTo("https://kakaoapi.aligo.in/akv10/alimtalk/send/"))
        .andExpect(content().string(Matchers.containsString("receiver_1=01012345678")))
        .andExpect(content().string(Matchers.containsString("failover_1=N")))
        .andRespond(
            withSuccess(
                "{\"code\":0,\"message\":\"success\",\"info\":{\"mid\":\"12345\"}}",
                MediaType.TEXT_HTML));

    var result = client.send("010-1234-5678", "상품 발송 안내", "발송 본문");

    assertThat(result.status()).isEqualTo(AligoAlimtalkClient.ResultStatus.ACCEPTED);
    assertThat(result.messageId()).isEqualTo("12345");
  }

  @Test
  void nonzeroSendCodeIsFailed() {
    server
        .expect(requestTo("https://kakaoapi.aligo.in/akv10/alimtalk/send/"))
        .andRespond(
            withSuccess(
                "{\"code\":-101,\"message\":\"인증 오류\"}", MediaType.APPLICATION_JSON));

    assertThat(client.send("01012345678", "제목", "본문").status())
        .isEqualTo(AligoAlimtalkClient.ResultStatus.FAILED);
  }

  @Test
  void configuredFinalResultCodeMeansRecipientSuccess() {
    server
        .expect(requestTo("https://kakaoapi.aligo.in/akv10/history/detail/"))
        .andExpect(content().string(Matchers.containsString("mid=12345")))
        .andRespond(
            withSuccess(
                "{\"code\":0,\"list\":[{\"msgid\":\"1\",\"phone\":\"01012345678\",\"status\":\"완료\",\"rslt\":\"0000\",\"rslt_message\":\"성공\"}]}",
                MediaType.TEXT_HTML));

    assertThat(client.query("12345", "010-1234-5678").status())
        .isEqualTo(AligoAlimtalkClient.ResultStatus.SUCCEEDED);
  }

  @Test
  void missingOrInProgressDetailRemainsUnknown() {
    server
        .expect(requestTo("https://kakaoapi.aligo.in/akv10/history/detail/"))
        .andRespond(
            withSuccess(
                "{\"code\":0,\"list\":[{\"msgid\":\"Q123\",\"phone\":\"01012345678\",\"status\":\"전송중\",\"rslt\":\"\"}]}",
                MediaType.APPLICATION_JSON));

    assertThat(client.query("12345", "01012345678").status())
        .isEqualTo(AligoAlimtalkClient.ResultStatus.UNKNOWN);
  }

  @Test
  void unconfiguredFinalResultCodeIsFailed() {
    server
        .expect(requestTo("https://kakaoapi.aligo.in/akv10/history/detail/"))
        .andRespond(
            withSuccess(
                "{\"code\":0,\"list\":[{\"msgid\":\"1\",\"phone\":\"01012345678\",\"status\":\"완료\",\"rslt\":\"9999\",\"rslt_message\":\"수신 실패\"}]}",
                MediaType.APPLICATION_JSON));

    assertThat(client.query("12345", "01012345678").status())
        .isEqualTo(AligoAlimtalkClient.ResultStatus.FAILED);
  }
}
