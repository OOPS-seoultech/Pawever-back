package com.pawever.backend.workflow;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter @Component
@ConfigurationProperties(prefix = "figurine.image")
public class ImageGenerationProperties {
  private boolean enabled;
  private boolean workerEnabled=true;
  private boolean registerWebhooks=true;
  private String apiKey = "";
  private String baseUrl = "https://api.openai.com/v1";
  private String model = "gpt-image-2.5-sunburst";
  private String publicApiUrl = "https://api.pawever.kr";
  private String publicWebUrl = "https://www.pawever.kr";
  public boolean ready() { return enabled && apiKey != null && !apiKey.isBlank(); }
}
