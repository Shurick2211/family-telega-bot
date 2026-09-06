package org.nimko.com.config;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.ZoneId;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.ai.AiChatServiceAudioBook;
import org.nimko.com.bot.BotSenderService;
import org.nimko.com.bot.FamilyTelegramBot;
import org.nimko.com.bot.commands.CommandProcess;
import org.nimko.com.repository.ChatContextRepository;
import org.nimko.com.services.AudioConverter;
import org.nimko.com.services.MediaDownloadService;
import org.nimko.com.services.TelegramFileService;
import org.nimko.com.services.I18nService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

@Configuration
@Slf4j
@EnableConfigurationProperties({TelegramBotProperties.class, AiChatProperties.class})
public class AppConfig {

  @Bean
  AiChatService aiChatService(final AiChatProperties aiProperties,
      final @Value("${transcription-model}") String transcriptionModel) {
    return new AiChatService(transcriptionModel, aiProperties);
  }

  @Bean
  AiChatServiceAudioBook aiChatServiceAudioBook(final AiChatProperties aiProperties,
      final @Value("${tts-model}") String ttsModel,
      final @Value("${tts-voice:}") String ttsVoice,
      final @Value("${transcription-model}") String transcriptionModel,
      final AudioConverter audioConverter) {
    return new AiChatServiceAudioBook(ttsModel, ttsVoice, transcriptionModel, aiProperties, audioConverter);
  }

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
            new FamilyTelegramBot(telegramProperties.username(), aiChatService, audioConverter,
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

  @Bean
  ZoneId appZoneId(final @Value("${app.timezone}") String timezone) {
    final ZoneId zoneId = ZoneId.of(timezone);
    log.info("Application timezone: {} (JVM default: {})", zoneId, ZoneId.systemDefault());
    return zoneId;
  }

  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper()
        .configure(WRITE_DATES_AS_TIMESTAMPS, false)
        .registerModule(new Jdk8Module())
        .registerModule(new JavaTimeModule());
  }
}
