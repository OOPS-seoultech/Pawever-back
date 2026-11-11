package com.pawever.backend.workflow;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.pawever.backend.notification.telegram.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
class ImageTelegramClientTest {
  @Test void webhookSecretIsRequiredAndUnrelatedHooksAreNeverReplaced() {
    var http=new RestTemplate();var server=MockRestServiceServer.bindTo(http).build();
    var payment=new TelegramProperties();payment.setBotToken("payment-test");payment.setChatId("-1");
    var review=new TelegramOpsProperties();review.setBotToken("review-test");review.setChatId("-2");
    var config=new ImageGenerationProperties();config.setEnabled(true);config.setApiKey("test");
    var client=new ImageTelegramClient(http,payment,review,new TelegramOpsClient(http,review),config);
    assertThat(client.authorized("payment",null)).isFalse();
    assertThat(client.authorized("other",client.secret("payment"))).isFalse();
    assertThat(client.authorized("review",client.secret("payment"))).isFalse();
    assertThat(client.authorized("payment",client.secret("payment"))).isTrue();
    server.expect(requestTo("https://api.telegram.org/botpayment-test/getWebhookInfo")).andRespond(withSuccess("{\"ok\":true,\"result\":{\"url\":\"https://other.example/webhook\"}}",MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://api.telegram.org/botreview-test/getWebhookInfo")).andRespond(withSuccess("{\"ok\":true,\"result\":{\"url\":\"\"}}",MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://api.telegram.org/botreview-test/setWebhook")).andExpect(jsonPath("$.secret_token").value(client.secret("review"))).andExpect(jsonPath("$.drop_pending_updates").value(false)).andRespond(withSuccess("{\"ok\":true,\"result\":true}",MediaType.APPLICATION_JSON));
    client.register();assertThat(client.webhookStatus()).containsEntry("payment","CONFLICT_EXISTING_WEBHOOK").containsEntry("review","CONNECTED");server.verify();
  }
  @Test void sendsTwoHarmlessButtonsToEachConfiguredRoom() {
    var http=new RestTemplate();var server=MockRestServiceServer.bindTo(http).build();
    var payment=new TelegramProperties();payment.setBotToken("payment-test");payment.setChatId("-1");
    var review=new TelegramOpsProperties();review.setBotToken("review-test");review.setChatId("-2");
    var client=new ImageTelegramClient(http,payment,review,new TelegramOpsClient(http,review),new ImageGenerationProperties());
    for(String kind:java.util.List.of("payment","review")) {
      String token=kind.equals("payment")?"payment-test":"review-test";
      server.expect(requestTo("https://api.telegram.org/bot"+token+"/getChat")).andRespond(withSuccess(kind.equals("payment")?"{\"ok\":true,\"result\":{\"first_name\":\"대표 수신자\",\"type\":\"private\"}}":"{\"ok\":true,\"result\":{\"title\":\"PAWEVER\",\"type\":\"group\"}}",MediaType.APPLICATION_JSON));
      if(kind.equals("payment"))server.expect(requestTo("https://api.telegram.org/bot"+token+"/getMe")).andRespond(withSuccess("{\"ok\":true,\"result\":{\"first_name\":\"PAWEVER\"}}",MediaType.APPLICATION_JSON));
      server.expect(requestTo("https://api.telegram.org/bot"+token+"/sendMessage"))
        .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("실제 주문 아님")))
        .andExpect(jsonPath("$.reply_markup.inline_keyboard[0].length()").value(2))
        .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][0].callback_data").value("test:"+kind))
        .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][1].url").value(org.hamcrest.Matchers.startsWith("https://www.pawever.kr/admin/")))
        .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":12}}",MediaType.APPLICATION_JSON));
    }
    assertThat(client.sendTest("payment","대표")).containsEntry("accepted",true).containsEntry("title","PAWEVER");
    assertThat(client.sendTest("review","대표")).containsEntry("messageId",12L);server.verify();
  }

  @Test void productionMessagesUseExactlyTheRequestedTwoButtonsAndOrderSpecificLink() {
    var http=new RestTemplate();var server=MockRestServiceServer.bindTo(http).build();
    var payment=new TelegramProperties();payment.setBotToken("payment-test");payment.setChatId("-1");
    var review=new TelegramOpsProperties();review.setBotToken("review-test");review.setChatId("-2");
    var albums=org.mockito.Mockito.mock(TelegramOpsClient.class);
    org.mockito.Mockito.when(albums.sendAlbum(org.mockito.ArgumentMatchers.any())).thenReturn(true);
    var client=new ImageTelegramClient(http,payment,review,albums,new ImageGenerationProperties());
    server.expect(requestTo("https://api.telegram.org/botpayment-test/sendMessage"))
      .andExpect(jsonPath("$.reply_markup.inline_keyboard.length()").value(1))
      .andExpect(jsonPath("$.reply_markup.inline_keyboard[0].length()").value(2))
      .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][0].text").value("입금 확인"))
      .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][0].callback_data").value("pay:PE-TEST-1"))
      .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][1].text").value("관리자 사이트"))
      .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][1].url").value("https://www.pawever.kr/admin/orders/PE-TEST-1"))
      .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":1}}",MediaType.APPLICATION_JSON));
    var event=new com.pawever.backend.goodssurvey.event.GoodsOrderSubmittedEvent("PE-TEST-1","테스트 보호자","01000000000","테스트 아이","피규어",37800,false,"직접 유입",2880);
    String id="00000000-0000-0000-0000-000000000001";
    server.expect(requestTo("https://api.telegram.org/botreview-test/sendMessage"))
      .andExpect(jsonPath("$.reply_markup.inline_keyboard.length()").value(1))
      .andExpect(jsonPath("$.reply_markup.inline_keyboard[0].length()").value(2))
      .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][0].text").value("검수 완료"))
      .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][1].text").value("수정 필요"))
      .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][1].callback_data").value("changes:"+id))
      .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":2}}",MediaType.APPLICATION_JSON));
    assertThat(client.order(event)).isTrue();
    assertThat(client.review(id,"PE-TEST-1",0,new ModelReviewMedia.Album("검수 대기",java.util.List.of()))).isEqualTo(2L);
    server.verify();
  }

  @Test void memoUsesSelectiveReplyAndEscapesStaffAndPetNames() {
    var http=new RestTemplate();var server=MockRestServiceServer.bindTo(http).build();
    var ops=new TelegramOpsProperties();ops.setBotToken("review-test");ops.setChatId("-2");
    var client=new ImageTelegramClient(http,new TelegramProperties(),ops,new TelegramOpsClient(http,ops),new ImageGenerationProperties());
    server.expect(requestTo("https://api.telegram.org/botreview-test/sendMessage"))
      .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("tg://user?id=777")))
      .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("&lt;제작자&gt;")))
      .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("&lt;아이&gt;")))
      .andExpect(jsonPath("$.reply_markup.force_reply").value(true))
      .andExpect(jsonPath("$.reply_markup.selective").value(true))
      .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":99}}",MediaType.APPLICATION_JSON));
    assertThat(client.feedbackPrompt("-2",777L,"<제작자>","<아이>")).isEqualTo(99L);server.verify();
  }
  @Test void onlyUnchangedEditsAreIgnoredAndOtherTelegramErrorsSurface() {
    var http=new RestTemplate();var server=MockRestServiceServer.bindTo(http).build();
    var ops=new TelegramOpsProperties();ops.setBotToken("review-test");
    var client=new ImageTelegramClient(http,new TelegramProperties(),ops,new TelegramOpsClient(http,ops),new ImageGenerationProperties());
    server.expect(requestTo("https://api.telegram.org/botreview-test/editMessageText"))
      .andRespond(withBadRequest().body("{\"description\":\"Bad Request: message is not modified\"}"));
    server.expect(requestTo("https://api.telegram.org/botreview-test/editMessageText"))
      .andRespond(withBadRequest().body("{\"description\":\"Bad Request: chat not found\"}"));
    client.editReview("-2",99L,"same",java.util.List.of());
    assertThatThrownBy(()->client.editReview("-2",99L,"new",java.util.List.of())).isInstanceOf(WorkflowException.class);server.verify();
  }

  @Test void checkboxAcknowledgementDoesNotOpenABlockingAlert() {
    var http=new RestTemplate();var server=MockRestServiceServer.bindTo(http).build();
    var ops=new TelegramOpsProperties();ops.setBotToken("review-test");
    var client=new ImageTelegramClient(http,new TelegramProperties(),ops,new TelegramOpsClient(http,ops),new ImageGenerationProperties());
    server.expect(requestTo("https://api.telegram.org/botreview-test/answerCallbackQuery"))
      .andExpect(jsonPath("$.show_alert").value(false))
      .andRespond(withSuccess("{\"ok\":true,\"result\":true}",MediaType.APPLICATION_JSON));
    client.answer("review","select-cb","수정 항목을 저장했습니다.",false);server.verify();
  }

}
