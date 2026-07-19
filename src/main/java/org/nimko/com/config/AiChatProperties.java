package org.nimko.com.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.apache.commons.lang3.StringUtils;

@ConfigurationProperties(prefix = "ai")
public record AiChatProperties(
    String apiBaseUrl,
    String apiKey,
    String apiKeySecondary,
    boolean enableSecondary,
    String defaultModel,
    String systemPrompt) {

  public boolean isConfigured() {
    return StringUtils.isNotBlank(apiBaseUrl)
        && StringUtils.isNotBlank(apiKey)
        && StringUtils.isNotBlank(defaultModel)
        && StringUtils.isNotBlank(apiKeySecondary);
  }
}
