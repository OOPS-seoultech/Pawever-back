package com.pawever.backend.workflow;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class OpenAiImageClientTest {
  static byte[] sheet() throws IOException {
    var image=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_RGB);
    int[] colors={0xff0000,0x00ff00,0x0000ff,0xffffff};
    for(int y=0;y<1024;y++)for(int x=0;x<1024;x++)image.setRGB(x,y,colors[(y/512)*2+x/512]);
    var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
  }
  @Test void splitsFrontLeftBackRightAndRejectsInvalidImages() throws Exception {
    var views=OpenAiImageClient.split(sheet());assertThat(views).hasSize(4);
    int[] expected={0xff0000,0x00ff00,0x0000ff,0xffffff};
    for(int i=0;i<4;i++){var image=ImageIO.read(new ByteArrayInputStream(views.get(i)));assertThat(image.getWidth()).isEqualTo(512);assertThat(image.getRGB(0,0)&0xffffff).isEqualTo(expected[i]);}
    assertThatThrownBy(()->OpenAiImageClient.split(new byte[]{1,2})).isInstanceOf(IOException.class);
  }
  @Test void sendsOneSheetWithAllReferencesAndDoesNotExposeApiErrors() throws Exception {
    var config=new ImageGenerationProperties();config.setEnabled(true);config.setApiKey("test-key");
    var client=new OpenAiImageClient(config);
    var server=MockRestServiceServer.bindTo((RestTemplate)ReflectionTestUtils.getField(client,"http")).build();
    server.expect(requestTo("https://api.openai.com/v1/images/edits"))
      .andExpect(header("Authorization","Bearer test-key"))
      .andExpect(content().string(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("name=\"image[]\""),org.hamcrest.Matchers.containsString("previous.png"),org.hamcrest.Matchers.containsString("photo.png"),org.hamcrest.Matchers.containsString("2048x2048"))))
      .andRespond(withSuccess("{\"data\":[{\"b64_json\":\""+Base64.getEncoder().encodeToString(sheet())+"\"}],\"usage\":{\"total_tokens\":1}}",MediaType.APPLICATION_JSON));
    var result=client.generate(List.of(new OpenAiImageClient.Input("previous.png",sheet(),"image/png"),new OpenAiImageClient.Input("photo.png",sheet(),"image/png")),"edit only tail");
    assertThat(result.views()).hasSize(4);server.verify();server.reset();
    server.expect(requestTo("https://api.openai.com/v1/images/edits")).andRespond(withUnauthorizedRequest().body("private echoed data"));
    assertThatThrownBy(()->client.generate(List.of(),"prompt")).isInstanceOf(WorkflowException.class).hasMessageNotContaining("private");server.verify();server.reset();
    server.expect(requestTo("https://api.openai.com/v1/images/edits")).andRespond(withException(new java.net.SocketTimeoutException()));
    assertThatThrownBy(()->client.generate(List.of(),"prompt")).isInstanceOfSatisfying(WorkflowException.class,e->assertThat(e.getCode()).isEqualTo("IMAGE_RESULT_UNKNOWN"));server.verify();
  }
}
