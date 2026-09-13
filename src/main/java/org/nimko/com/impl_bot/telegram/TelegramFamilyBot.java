package org.nimko.com.impl_bot.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.bot.BotSenderService;
import org.nimko.com.bot.FamilyBot;
import org.nimko.com.bot.commands.CommandProcess;
import org.nimko.com.bot.messenger.MediaFileService;
import org.nimko.com.impl_bot.telegram.messanger.TelegramIncomingMessage;
import org.nimko.com.impl_bot.telegram.messanger.TelegramReactionEvent;
import org.nimko.com.repository.ChatContextRepository;
import org.nimko.com.services.AudioConverter;
import org.nimko.com.services.I18nService;
import org.nimko.com.services.MediaDownloadService;
import org.nimko.com.services.TranslationContext;
import org.nimko.com.impl_bot.telegram.TelegramBotUtils.ReplyPayload;
import org.nimko.com.util.BotUtils;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
public class TelegramFamilyBot extends FamilyBot implements LongPollingUpdateConsumer {

  public TelegramFamilyBot(final String botUsername, final AiChatService aiChatService,
      final AudioConverter audioConverter, final MediaDownloadService mediaDownloadService,
      final boolean needAutoTranscribe, final I18nService i18nService,
      final BotSenderService botSenderService, final List<CommandProcess> commandProcesses,
      final MediaFileService mediaFileService, final ChatContextRepository chatContextRepository,
      final ObjectMapper objectMapper, final long newsChatId) {
    super(botUsername, aiChatService, audioConverter, mediaDownloadService, needAutoTranscribe,
        i18nService, botSenderService, commandProcesses, mediaFileService, chatContextRepository,
        objectMapper, newsChatId);
  }

  @Override
  public void consume(final List<Update> updates) {
    for (final Update update : updates) {
      if (update == null) {
        continue;
      }

      try {
        if (update.hasCallbackQuery()) {
          final var from = update.getCallbackQuery() != null ? update.getCallbackQuery().getFrom()
              : null;
          TranslationContext.setLocale(
              BotUtils.resolveLocale(from != null ? from.getLanguageCode() : null));
          handleCallbackQuery(update.getCallbackQuery());
          continue;
        }

        if (update.getMessageReaction() != null) {
          final TelegramReactionEvent reactionEvent =
              new TelegramReactionEvent(update.getMessageReaction());
          TranslationContext.setLocale(resolveLocale(reactionEvent));
          handleReaction(reactionEvent);
          continue;
        }

        if (!update.hasMessage()) {
          continue;
        }

        final TelegramIncomingMessage message = new TelegramIncomingMessage(update.getMessage());
        if (!message.hasUserContent()) {
          continue;
        }

        TranslationContext.setLocale(resolveLocale(message));
        log.info("Received Telegram message: chatId={} text={} hasPhoto={}",
            message.getChatId(), message.getText(), message.hasPhoto());
        handleMessage(message);
      } catch (final Exception ex) {
        log.error("Unexpected error while handling update, skipping it", ex);
      } finally {
        TranslationContext.clear();
      }
    }
  }

  private void handleCallbackQuery(final CallbackQuery callbackQuery) {
    if (callbackQuery == null || StringUtils.isBlank(callbackQuery.getData())
        || callbackQuery.getMessage() == null) {
      return;
    }

    final String data = callbackQuery.getData();
    if (!data.startsWith(TelegramBotSenderService.COPY_IMG_CALLBACK_PREFIX)) {
      return;
    }

    final String token = data.substring(TelegramBotSenderService.COPY_IMG_CALLBACK_PREFIX.length());
    final ReplyPayload payload = TelegramBotUtils.getCopyImagePayload(token);
    if (payload == null) {
      botSenderService.answerCallbackQuery(callbackQuery.getId(),
          i18nService.getTranslate("bot.callback.image.unavailable"));
      return;
    }

    if (!(botSenderService instanceof final TelegramBotSenderService telegramSender)) {
      return;
    }

    final Long chatId = callbackQuery.getMessage().getChatId();
    final boolean sent = telegramSender.sendCopiedImage(chatId, payload);
    if (sent) {
      TelegramBotUtils.removeCopyImagePayload(token);
      botSenderService.answerCallbackQuery(callbackQuery.getId(), null);
    } else {
      botSenderService.answerCallbackQuery(callbackQuery.getId(),
          i18nService.getTranslate("bot.callback.image.failed"));
    }
  }

}
