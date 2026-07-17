package org.nimko.com;

import org.nimko.com.ai.AiChatService;
import org.nimko.com.bot.HelloHelpTelegramBot;
import org.nimko.com.config.AiChatProperties;
import org.nimko.com.config.TelegramBotProperties;
import org.nimko.com.services.AudioConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

@SpringBootApplication
@EnableConfigurationProperties({TelegramBotProperties.class, AiChatProperties.class})
@EnableScheduling
public class Main {

  private static final Logger log = LoggerFactory.getLogger(Main.class);

  public static void main(final String[] args) {
    SpringApplication.run(Main.class, args);
  }

  @Bean
  AiChatService aiChatService(final AiChatProperties aiProperties, final @Value("${transcription-model}") String transcriptionModel) {
    return new AiChatService(transcriptionModel, aiProperties);
  }

  @Bean(destroyMethod = "close")
  TelegramBotsLongPollingApplication telegramBotsLongPollingApplication() {
    return new TelegramBotsLongPollingApplication();
  }

  @Bean
  CommandLineRunner telegramBotRunner(
      final TelegramBotsLongPollingApplication telegramBotsLongPollingApplication,
      final TelegramBotProperties telegramProperties,
      final AiChatService aiChatService,
      final AiChatProperties aiProperties,
      final AudioConverter audioConverter
  ) {
    return args -> {
      if (!telegramProperties.isConfigured()) {
        log.warn("Telegram bot is disabled. Set telegram.bot.username and telegram.bot.token to enable it.");
        return;
      }

      try {
        telegramBotsLongPollingApplication.registerBot(
            telegramProperties.token(),
            new HelloHelpTelegramBot(telegramProperties, aiChatService, audioConverter, telegramProperties.needAutoTranscribe(), telegramProperties.newsChatId(), telegramProperties.downloaderEndpoint()));
        log.info("Telegram bot registered: {}", telegramProperties.username());
        log.info("AI model configured: {}", aiProperties.defaultModel());
      } catch (final Exception ex) {
        log.error("Failed to register Telegram bot {}", telegramProperties.username(), ex);
      }
    };
  }
}
