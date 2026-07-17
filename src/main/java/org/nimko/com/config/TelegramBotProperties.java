package org.nimko.com.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

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
    return StringUtils.hasText(username) && StringUtils.hasText(token);
  }
}
