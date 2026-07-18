package org.nimko.com.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "ai")
public record AiChatProperties(
    String apiBaseUrl,
    String apiKey,
    String apiKeySecondary,
    boolean enableSecondary,
    String defaultModel,
    String systemPrompt) {

  public boolean isConfigured() {
    return StringUtils.hasText(apiBaseUrl)
        && StringUtils.hasText(apiKey)
        && StringUtils.hasText(defaultModel)
        && StringUtils.hasText(apiKeySecondary);
  }
}
