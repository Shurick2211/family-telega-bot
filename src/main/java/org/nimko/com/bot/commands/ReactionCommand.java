package org.nimko.com.bot.commands;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nimko.com.bot.FamilyTelegramBot.ReplyData;
import org.nimko.com.repository.ChatContextRepository;
import org.nimko.com.util.BotUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.reactions.MessageReactionUpdated;

import static org.nimko.com.util.BotUtils.addTranscribedInContext;

@Service
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class ReactionCommand implements CommandProcess {

  private final ChatContextRepository chatContextRepository;

  @Override
  public boolean isCommand(final String command) {
    return false;
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final Message message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId) {
    return null;
  }

  @Override
  public void handleReaction(final MessageReactionUpdated messageReaction) {
    if (messageReaction == null) {
      return;
    }

    final Long chatId = messageReaction.getChat() != null ? messageReaction.getChat().getId() : null;
    if (chatId == null) {
      return;
    }

    final Integer messageId = messageReaction.getMessageId();
    if (messageId == null) {
      return;
    }

    final var user = messageReaction.getUser();
    final String telegramUser = user != null ? BotUtils.getSenderName(user) : "Unknown";
    final String username = user != null ? BotUtils.getSenderName(user) : "Unknown";
    final boolean groupChat = BotUtils.isGroupChat(messageReaction.getChat());

    if (messageReaction.getNewReaction() != null) {
      for (final var reaction : messageReaction.getNewReaction()) {
        final String reactionValue = BotUtils.getReactionString(reaction);
        if (StringUtils.isNotBlank(reactionValue)) {
          log.info("Received reaction: reaction={} chatId={} messageId={} user={}",
              reactionValue, chatId, messageId, telegramUser);
          addTranscribedInContext(telegramUser, username, "[emotion] " + reactionValue, chatId, messageId, chatContextRepository, groupChat);
        }
      }
    }
  }
}
