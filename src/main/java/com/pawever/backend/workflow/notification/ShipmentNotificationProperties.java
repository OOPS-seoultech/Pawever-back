package com.pawever.backend.workflow.notification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 승인된 알림톡 발송 계약. 하나라도 비거나 본문 hash가 다르면 발송하지 않는다. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notification.alimtalk", ignoreUnknownFields = false)
public class ShipmentNotificationProperties {
  private boolean enabled;
  private String apiKey = "";
  private String userId = "";
  private String senderKey = "";
  private String sender = "";
  private String templateCode = "";
  private String subject = "";
  private String approvedBody = "";
  private String approvedBodyBase64 = "";
  private String approvedBodySha256 = "";
  private String baseUrl = "https://kakaoapi.aligo.in";
  private boolean testMode;
  private List<String> successResultCodes = List.of();

  public boolean isReady() {
    return enabled
        && nonBlank(apiKey)
        && nonBlank(userId)
        && nonBlank(senderKey)
        && nonBlank(sender)
        && nonBlank(templateCode)
        && nonBlank(subject)
        && nonBlank(body())
        && nonBlank(approvedBodySha256)
        && successResultCodes != null
        && successResultCodes.stream().anyMatch(ShipmentNotificationProperties::nonBlank)
        && approvedBodySha256.equalsIgnoreCase(hash(body()));
  }

  String body() {
    if (!nonBlank(approvedBodyBase64)) return approvedBody;
    try {
      return new String(Base64.getDecoder().decode(approvedBodyBase64), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException invalidBase64) {
      return "";
    }
  }

  boolean isSuccessCode(String code) {
    return code != null
        && successResultCodes != null
        && successResultCodes.stream().map(String::strip).anyMatch(code::equals);
  }

  private static boolean nonBlank(String value) {
    return value != null && !value.isBlank();
  }

  static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
