package com.pawever.backend.workflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShipmentNotificationPropertiesTest {
  @Test
  void blankSuccessCodeCannotEnableRealSending() {
    var properties = configured();
    properties.setSuccessResultCodes(List.of(""));

    assertThat(properties.isReady()).isFalse();
  }

  @Test
  void approvedBodyHashMustMatchExactly() {
    var properties = configured();
    properties.setApprovedBodySha256(ShipmentNotificationProperties.hash("다른 본문"));

    assertThat(properties.isReady()).isFalse();
  }

  @Test
  void multilineApprovedBodyCanBeSuppliedAsBase64() {
    var properties = configured();
    String body = "첫 줄\n#{송장번호}";
    properties.setApprovedBody("");
    properties.setApprovedBodyBase64(
        Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)));
    properties.setApprovedBodySha256(ShipmentNotificationProperties.hash(body));

    assertThat(properties.isReady()).isTrue();
    assertThat(properties.body()).isEqualTo(body);
  }

  private ShipmentNotificationProperties configured() {
    var properties = new ShipmentNotificationProperties();
    properties.setEnabled(true);
    properties.setApiKey("key");
    properties.setUserId("user");
    properties.setSenderKey("sender-key");
    properties.setSender("0212345678");
    properties.setTemplateCode("template");
    properties.setSubject("subject");
    properties.setApprovedBody("approved body");
    properties.setApprovedBodySha256(
        ShipmentNotificationProperties.hash(properties.getApprovedBody()));
    properties.setSuccessResultCodes(List.of("0000"));
    return properties;
  }
}
