package org.nimko.com.bot.commands;

import org.nimko.com.bot.dto.ReplyData;
import org.nimko.com.bot.messenger.IncomingMessage;
import org.nimko.com.bot.messenger.ReactionEvent;

public interface CommandProcess {

  boolean isCommand(String command);

  default void handleReaction(final ReactionEvent reactionEvent) {
  }

  ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final IncomingMessage message, final Long chatId, final boolean hasVoice,
      final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId);

}
