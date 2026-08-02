package org.nimko.com.bot.commands;

import java.util.concurrent.atomic.AtomicBoolean;
import org.nimko.com.bot.BotSenderService;
import org.nimko.com.bot.FamilyTelegramBot.ReplyData;
import org.nimko.com.services.I18nService;
import org.nimko.com.services.TermuxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Service
@Order(1)
public class LightCommand implements CommandProcess {

  private static final Logger log = LoggerFactory.getLogger(LightCommand.class);

  private final TermuxService termuxService;
  private final BotSenderService botSenderService;
  private final I18nService i18nService;
  private final AtomicBoolean flashlightOn = new AtomicBoolean(false);

  public LightCommand(final TermuxService termuxService,
      final BotSenderService botSenderService,
      final I18nService i18nService) {
    this.termuxService = termuxService;
    this.botSenderService = botSenderService;
    this.i18nService = i18nService;
  }

  @Override
  public boolean isCommand(final String command) {
    return "/light".equals(command);
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final Message message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId) {
    final boolean enable = !flashlightOn.get();
    try {
      termuxService.setFlashlight(enable);
      flashlightOn.set(enable);
      botSenderService.sendTextReply(chatId,
          i18nService.getTranslate(enable ? "bot.light.on" : "bot.light.off"));
    } catch (final Exception ex) {
      log.error("Error processing /light command for chat {}", chatId, ex);
      botSenderService.sendTextReply(chatId, i18nService.getTranslate("bot.light.error", ex.getMessage()));
    }
    return null;
  }
}
