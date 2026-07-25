package org.nimko.com.bot.commands;

import static org.nimko.com.repository.ChatContextRepository.getTodayContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.nimko.com.bot.FamilyTelegramBot.ReplyData;
import org.nimko.com.repository.ChatContextRepository;
import org.nimko.com.services.TranslationService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Service
@RequiredArgsConstructor
@Order(1)
public class ContextCommand implements CommandProcess {

  private final TranslationService translationService;
  private final ChatContextRepository chatContextRepository;
  private final ObjectMapper objectMapper;

  @Override
  public boolean isCommand(final String command) {
    return "/context".equals(command);
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final Message message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId) {
    final var contextList = getTodayContext(chatContextRepository, objectMapper, chatId);
    return new ReplyData(
        contextList != null ? String.join("\n", contextList)
            : translationService.getTranslate("bot.context.empty"), false);
  }
}
