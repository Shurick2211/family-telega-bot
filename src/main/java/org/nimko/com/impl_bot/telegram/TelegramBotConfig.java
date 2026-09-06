package org.nimko.com.impl_bot.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.bot.BotSenderService;
import org.nimko.com.bot.commands.CommandProcess;
import org.nimko.com.config.AiChatProperties;
import org.nimko.com.repository.ChatContextRepository;
import org.nimko.com.services.AudioConverter;
import org.nimko.com.services.I18nService;
import org.nimko.com.services.MediaDownloadService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

@Configuration
@Slf4j
@EnableConfigurationProperties({TelegramBotProperties.class})
public class TelegramBotConfig {

  @Bean(destroyMethod = "close")
  TelegramBotsLongPollingApplication telegramBotsLongPollingApp() {
    return new TelegramBotsLongPollingApplication();
  }

  @Bean
  CommandLineRunner telegramBotRunner(
      final TelegramBotsLongPollingApplication telegramBotsLongPollingApplication,
      final TelegramBotProperties telegramProperties,
      final AiChatService aiChatService,
      final AiChatProperties aiProperties,
      final AudioConverter audioConverter,
      final I18nService i18nService,
      final BotSenderService botSenderService,
      final List<CommandProcess> commandProcesses,
      final TelegramFileService telegramFileService,
      final ChatContextRepository chatContextRepository,
      final ObjectMapper objectMapper
  ) {
    return args -> {
      if (!telegramProperties.isConfigured()) {
        log.warn("Telegram bot is disabled. Set telegram.bot.username and telegram.bot.token to enable it.");
        return;
      }

      try {
        final MediaDownloadService mediaDownloadService = new MediaDownloadService(
            botSenderService, telegramProperties.downloaderEndpoint());
        telegramBotsLongPollingApplication.registerBot(
            telegramProperties.token(),
            new TelegramFamilyBot(telegramProperties.username(), aiChatService, audioConverter,
                mediaDownloadService, telegramProperties.needAutoTranscribe(), i18nService,
                botSenderService, commandProcesses, telegramFileService, chatContextRepository,
                objectMapper, telegramProperties.newsChatId()));
        log.info("Telegram bot registered: {}", telegramProperties.username());
        log.info("AI model configured: {}", aiProperties.defaultModel());
      } catch (final Exception ex) {
        log.error("Failed to register Telegram bot {}", telegramProperties.username(), ex);
      }
    };
  }

}
