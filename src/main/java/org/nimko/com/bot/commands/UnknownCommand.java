package org.nimko.com.bot.commands;

import org.apache.commons.lang3.StringUtils;
import org.nimko.com.bot.dto.ReplyData;
import org.nimko.com.services.I18nService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.nimko.com.bot.messenger.IncomingMessage;

@Service
@Order(10000)
public class UnknownCommand implements CommandProcess {

  private final I18nService i18nService;

  public UnknownCommand(final I18nService i18nService) {
    this.i18nService = i18nService;
  }

  @Override
  public boolean isCommand(final String command) {
    return StringUtils.startsWith(command, "/");
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final IncomingMessage message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId) {
    return new ReplyData(i18nService.getTranslate("bot.command.unknown", normalizedText), false);
  }
}
