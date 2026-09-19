package com.pawever.backend.notification.telegram;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 제작 검수 알림 전용 봇. 기존 입금방 봇과 자격증명·대상을 공유하지 않는다. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notification.telegram-ops", ignoreUnknownFields = false)
public class TelegramOpsProperties {
  private String botToken = "";
  private String chatId = "";
  private String baseUrl = "https://api.telegram.org";

  public boolean isConfigured() {
    return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
  }
}
