package org.nimko.com.bot.commands;

import static org.nimko.com.util.BotUtils.addTranscribedInContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.bot.FamilyTelegramBot.ReplyData;
import org.nimko.com.config.TelegramBotProperties;
import org.nimko.com.util.BotUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Service
public class DefaultMessage implements CommandProcess{

  private final AiChatService aiChatService;
  private final String botUsername;
  private final long newsChatId;

  private static final Logger log = LoggerFactory.getLogger(DefaultMessage.class);

  public DefaultMessage(final AiChatService aiChatService,
      final TelegramBotProperties telegramProperties) {
    this.aiChatService = aiChatService;
    this.botUsername = telegramProperties.username();
    this.newsChatId = telegramProperties.newsChatId();
  }

  @Override
  public boolean isCommand(final String command) {
    return !StringUtils.startsWith(command, "/");
  }

  @Override
  public ReplyData execute(final String normalizedText,final boolean hasPhoto,
      final byte[] imageBytes,final Message message,final Long chatId, final boolean hasVoice,
      final byte[] rawAudioBytes, final byte[] extractedAudioFromVideoBytes,
      final boolean groupChat,final int messageId, final Map<Long, List<String>> chatContext) {

    final List<String> currentHistory = chatContext.getOrDefault(chatId, Collections.emptyList());
    final String prompt = groupChat
        ? BotUtils.stripBotPrefix(normalizedText, botUsername, currentHistory, groupChat)
        : normalizedText;

    if (groupChat && StringUtils.isNotBlank(normalizedText)) {
      final String authorUsername = BotUtils.getSenderName(message.getFrom());
      addTranscribedInContext(authorUsername, authorUsername,
          BotUtils.stripTextPrefix(normalizedText), chatId, messageId, chatContext);
    }

    if (hasPhoto) {
      final String imagePrompt =
          StringUtils.isNotBlank(normalizedText) ? prompt : "Describe the image in detail.";
      log.info("Image prompt: {}", imagePrompt);
      return chatId == newsChatId && !groupChat
          ? new ReplyData(
          aiChatService.askNewsWithImage(BotUtils.prepareNewsPrompt(imagePrompt), imageBytes,
              "image/jpeg"), true)
          : new ReplyData(aiChatService.askWithImage(imagePrompt, imageBytes, "image/jpeg"), false);
    }

    if (StringUtils.isBlank(prompt)) {
      return null;
    }

    log.debug("Prompt sent to AI: {}", prompt);
    final var result = isNews(chatId) && !groupChat ?
        new ReplyData(aiChatService.askNews(BotUtils.prepareNewsPrompt(prompt)), true)
        : new ReplyData(aiChatService.ask(prompt), false);

    if (groupChat && result.text() != null) {
      addTranscribedInContext(botUsername, "Bot", result.text(), chatId, messageId, chatContext);
    }

    return result;
  }


  private boolean isNews(final Long chatId) {
    return chatId == newsChatId;
  }
}
