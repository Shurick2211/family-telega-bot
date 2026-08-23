package org.nimko.com.bot.commands;

import org.apache.commons.lang3.StringUtils;
import org.nimko.com.ai.AiChatServiceAudioBook;
import org.nimko.com.bot.BotSenderService;
import org.nimko.com.bot.FamilyTelegramBot.ReplyData;
import org.nimko.com.services.AudioConverter;
import org.nimko.com.services.BookTextExtractorService;
import org.nimko.com.services.I18nService;
import org.nimko.com.services.TelegramFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Service
@Order(1)
public class BookCommand implements CommandProcess {

  private static final Logger log = LoggerFactory.getLogger(BookCommand.class);

  private final AiChatServiceAudioBook aiChatServiceAudioBook;
  private final BookTextExtractorService bookTextExtractorService;
  private final TelegramFileService telegramFileService;
  private final BotSenderService botSenderService;
  private final I18nService i18nService;

  public BookCommand(final AiChatServiceAudioBook aiChatServiceAudioBook,
      final BookTextExtractorService bookTextExtractorService,
      final TelegramFileService telegramFileService,
      final BotSenderService botSenderService,
      final I18nService i18nService) {
    this.aiChatServiceAudioBook = aiChatServiceAudioBook;
    this.bookTextExtractorService = bookTextExtractorService;
    this.telegramFileService = telegramFileService;
    this.botSenderService = botSenderService;
    this.i18nService = i18nService;
  }

  @Override
  public boolean isCommand(final String command) {
    return "/book".equals(command);
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final Message message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId) {
    final Message documentMessage = resolveDocumentMessage(message);
    if (documentMessage == null) {
      botSenderService.sendTextReply(chatId, i18nService.getTranslate("bot.book.usage"));
      return null;
    }

    final String filename = documentMessage.getDocument().getFileName();
    if (!bookTextExtractorService.isSupported(filename)) {
      botSenderService.sendTextReply(chatId, i18nService.getTranslate("bot.book.unsupported"));
      return null;
    }

    java.util.concurrent.CompletableFuture.runAsync(() -> {
      try {
        final byte[] fileBytes = telegramFileService.downloadAudioMessage(documentMessage);
        if (fileBytes == null || fileBytes.length == 0) {
          botSenderService.sendTextReply(chatId, i18nService.getTranslate("bot.book.download.error"));
          return;
        }

        final String bookText = bookTextExtractorService.extractText(fileBytes, filename);
        if (StringUtils.isBlank(bookText)) {
          botSenderService.sendTextReply(chatId, i18nService.getTranslate("bot.book.empty"));
          return;
        }

        final String preparingText = i18nService.getTranslate("bot.book.preparing");
        final Integer progressMessageId = botSenderService.sendTextAndGetMessageId(chatId, preparingText + " 0%");

        final byte[] mp3Bytes = aiChatServiceAudioBook.narrateBook(bookText, progress -> {
          if (progressMessageId != null) {
            botSenderService.editMessageText(chatId, progressMessageId, preparingText + " " + progress + "%");
          }
        });
        if (mp3Bytes == null || mp3Bytes.length == 0) {
          botSenderService.sendTextReply(chatId, i18nService.getTranslate("bot.book.tts.error"));
          return;
        }

        final String audioFilename = filename + "_" + System.currentTimeMillis() + ".mp3";
        botSenderService.sendAudioFile(chatId, mp3Bytes, audioFilename);
      } catch (final Exception ex) {
        log.error("Error processing /book command for chat {}", chatId, ex);
        botSenderService.sendTextReply(chatId, i18nService.getTranslate("bot.book.error", ex.getMessage()));
      }
    });
    return null;
  }

  private Message resolveDocumentMessage(final Message message) {
    if (message.hasDocument()) {
      return message;
    }
    final Message replyTo = message.getReplyToMessage();
    if (replyTo != null && replyTo.hasDocument()) {
      return replyTo;
    }
    return null;
  }
}
