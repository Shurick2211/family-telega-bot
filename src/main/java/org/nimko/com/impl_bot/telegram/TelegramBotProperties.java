package org.nimko.com.impl_bot.telegram;

import org.apache.commons.lang3.StringUtils;
import org.nimko.com.bot.BotProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram.bot")
public record TelegramBotProperties(
    String username,
    String token,
    String apiBaseUrl,
    String defaultModel,
    boolean needAutoTranscribe,
    long newsChatId,
    String downloaderEndpoint) implements BotProperties {

  public boolean isConfigured() {
    return StringUtils.isNotBlank(username) && StringUtils.isNotBlank(token);
  }
}
