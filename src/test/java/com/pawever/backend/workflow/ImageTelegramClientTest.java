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
}
