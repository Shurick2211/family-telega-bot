package org.nimko.com.bot.commands;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nimko.com.bot.BotProperties;
import org.nimko.com.bot.dto.ReplyData;
import org.nimko.com.repository.ChatContextRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.nimko.com.bot.messenger.IncomingMessage;
import org.nimko.com.bot.messenger.ReactionEvent;

import static org.nimko.com.util.BotUtils.addTranscribedInContext;

@Service
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class ReactionCommand implements CommandProcess {

  private final ChatContextRepository chatContextRepository;
  private final BotProperties botProperties;

  @Override
  public boolean isCommand(final String command) {
    return false;
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final IncomingMessage message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId) {
    return null;
  }

  @Override
  public void handleReaction(final ReactionEvent messageReaction) {
    if (messageReaction == null) {
      return;
    }

    final Long chatId = messageReaction.getChatId();
    if (chatId == null) {
      return;
    }

    if (chatId.equals(botProperties.newsChatId())) {
      return;
    }

    final Integer messageId = messageReaction.getMessageId();
    if (messageId == null) {
      return;
    }

    final var user = messageReaction.getUser();
    final String telegramUser = user != null ? user.getUsername() : "Unknown";
    final String username = user != null ? user.getPersonName() : "Unknown";
    final boolean groupChat = messageReaction.isGroupChat();

    for (final String reactionValue : messageReaction.getReactionValues()) {
      if (StringUtils.isNotBlank(reactionValue)) {
        log.info("Received reaction: reaction={} chatId={} messageId={} user={}",
            reactionValue, chatId, messageId, telegramUser);
        addTranscribedInContext(telegramUser, username, "[emotion] " + reactionValue, chatId, messageId, chatContextRepository, groupChat);
      }
    }
  }
}
