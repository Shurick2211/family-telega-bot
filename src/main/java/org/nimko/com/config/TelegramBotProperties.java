package org.nimko.com.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.apache.commons.lang3.StringUtils;

@ConfigurationProperties(prefix = "telegram.bot")
public record TelegramBotProperties(
    String username,
    String token,
    String apiBaseUrl,
    String defaultModel,
    boolean needAutoTranscribe,
    long newsChatId,
    String downloaderEndpoint) {

  public boolean isConfigured() {
    return StringUtils.isNotBlank(username) && StringUtils.isNotBlank(token);
  }
}
