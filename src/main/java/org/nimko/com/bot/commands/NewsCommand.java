package org.nimko.com.bot.commands;

import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.bot.FamilyTelegramBot.ReplyData;
import org.nimko.com.util.BotUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Service
@Order(1)
public class NewsCommand implements CommandProcess {

  private final AiChatService aiChatService;

  public NewsCommand(final AiChatService aiChatService) {
    this.aiChatService = aiChatService;
  }

  @Override
  public boolean isCommand(final String command) {
    return "/news".equals(command);
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final Message message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId, final Map<Long, List<String>> chatContext) {
    final String promptNews = BotUtils.extractCommandPayload(normalizedText);
    final String preparedPrompt = BotUtils.prepareNewsPrompt(StringUtils.isNotBlank(promptNews)
        ? promptNews
        : "Перепиши новину за інформацією отримавши інформацію з прикріпленого посилання.");

    return hasPhoto
        ? new ReplyData(aiChatService.askNewsWithImage(preparedPrompt, imageBytes, "image/jpeg"),
        true)
        : new ReplyData(aiChatService.askNews(preparedPrompt), true);
  }
}
