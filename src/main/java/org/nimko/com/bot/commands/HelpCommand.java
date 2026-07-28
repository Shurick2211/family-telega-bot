package org.nimko.com.bot.commands;

import org.nimko.com.bot.FamilyTelegramBot.ReplyData;
import org.nimko.com.services.I18nService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Service
@Order(1)
public class HelpCommand implements CommandProcess {

  private final I18nService i18nService;

  public HelpCommand(final I18nService i18nService) {
    this.i18nService = i18nService;
  }

  @Override
  public boolean isCommand(final String command) {
    return "/help".equals(command);
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final Message message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId) {
    return new ReplyData(i18nService.getTranslate("bot.help"), false);
  }
}
