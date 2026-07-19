package org.nimko.com.bot.commands;

import java.util.List;
import java.util.Map;
import org.nimko.com.bot.FamilyTelegramBot.ReplyData;
import org.nimko.com.services.TranslationService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Service
@Order(1)
public class ContextCommand implements CommandProcess {

  private final TranslationService translationService;

  public ContextCommand(final TranslationService translationService) {
    this.translationService = translationService;
  }

  @Override
  public boolean isCommand(final String command) {
    return "/context".equals(command);
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final Message message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId, final Map<Long, List<String>> chatContext) {
    final var contextList = chatContext.get(chatId);
    return new ReplyData(
        contextList != null ? String.join("\n", contextList)
            : translationService.getTranslate("bot.context.empty"), false);
  }
}
