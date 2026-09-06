package org.nimko.com.config;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.ai.AiChatServiceAudioBook;
import org.nimko.com.services.AudioConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
