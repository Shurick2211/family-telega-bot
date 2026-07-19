package org.nimko.com.bot.commands;

import java.util.List;
import java.util.Map;
import org.nimko.com.bot.FamilyTelegramBot.ReplyData;
import org.telegram.telegrambots.meta.api.objects.message.Message;

public interface CommandProcess {

  boolean isCommand(String command);

  ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final Message message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId, final Map<Long, List<String>> chatContext);

}

