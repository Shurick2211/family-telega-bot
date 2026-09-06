package org.nimko.com.bot.commands;

import static org.nimko.com.repository.ChatContextRepository.getTodayContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.nimko.com.bot.BotProperties;
import org.nimko.com.bot.dto.ReplyData;
import org.nimko.com.repository.ChatContextRepository;
import org.nimko.com.services.I18nService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.nimko.com.bot.messenger.IncomingMessage;

@Service
@RequiredArgsConstructor
@Order(1)
public class ContextCommand implements CommandProcess {

  private final I18nService i18nService;
  private final ChatContextRepository chatContextRepository;
  private final ObjectMapper objectMapper;
  private final BotProperties properties;

  @Override
  public boolean isCommand(final String command) {
    return "/context".equals(command);
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final IncomingMessage message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId) {
    final var contextList = getTodayContext(chatContextRepository, objectMapper, chatId, properties.newsChatId());
    return new ReplyData(
        contextList != null ? String.join("\n", contextList)
            : i18nService.getTranslate("bot.context.empty"), false);
  }
}
