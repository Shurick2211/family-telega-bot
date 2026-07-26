package org.nimko.com.bot.commands;

import org.nimko.com.bot.FamilyTelegramBot.ReplyData;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.reactions.MessageReactionUpdated;

public interface CommandProcess {

  boolean isCommand(String command);

  default void handleReaction(final MessageReactionUpdated messageReaction) {
  }

  ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final Message message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId);

}

