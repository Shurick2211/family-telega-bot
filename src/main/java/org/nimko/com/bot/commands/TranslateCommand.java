package org.nimko.com.bot.commands;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.StringUtils;
import org.nimko.com.bot.BotSenderService;
import org.nimko.com.bot.dto.ReplyData;
import org.nimko.com.bot.messenger.IncomingMessage;
import org.nimko.com.bot.messenger.MediaFileService;
import org.nimko.com.services.DocumentTranslationService;
import org.nimko.com.services.DocumentTranslationService.TranslationResult;
import org.nimko.com.services.I18nService;
import org.nimko.com.util.BotUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * Translates a book (.doc, .docx, .pdf) or a PowerPoint presentation (.ppt, .pptx) attached to the
 * message (or to the message it replies to) into the language typed after the dash and sends the
 * result back with the original formatting preserved — a presentation stays a presentation:
 * {@code /translate-en}, {@code /translate-de}, {@code /translate-uk}.
 */
@Service
@Order(1)
public class TranslateCommand implements CommandProcess {

  private static final Logger log = LoggerFactory.getLogger(TranslateCommand.class);

  private static final String COMMAND = "/translate";
  private static final String DEFAULT_LANGUAGE_CODE = "en";

  private final DocumentTranslationService documentTranslationService;
  private final MediaFileService mediaFileService;
  private final BotSenderService botSenderService;
  private final I18nService i18nService;

  public TranslateCommand(final DocumentTranslationService documentTranslationService,
      final MediaFileService mediaFileService,
      final BotSenderService botSenderService,
      final I18nService i18nService) {
    this.documentTranslationService = documentTranslationService;
    this.mediaFileService = mediaFileService;
    this.botSenderService = botSenderService;
    this.i18nService = i18nService;
  }

  @Override
  public boolean isCommand(final String command) {
    if (StringUtils.isBlank(command)) {
      return false;
    }
    final String normalized = command.toLowerCase(Locale.ROOT);
    return COMMAND.equals(normalized) || normalized.startsWith(COMMAND + "-");
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto,
      final byte[] imageBytes, final IncomingMessage message, final Long chatId,
      final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId) {
    final IncomingMessage documentMessage = resolveDocumentMessage(message);
    if (documentMessage == null) {
      botSenderService.sendTextReply(chatId, i18nService.getTranslate("bot.translate.usage"));
      return null;
    }

    final String filename = documentMessage.getDocumentFileName();
    if (!documentTranslationService.isSupported(filename)) {
      botSenderService.sendTextReply(chatId, i18nService.getTranslate("bot.translate.unsupported"));
      return null;
    }

    final String languageCode = resolveLanguageCode(normalizedText);

    CompletableFuture.runAsync(() -> {
      try {
        final byte[] fileBytes = mediaFileService.downloadAudioMessage(documentMessage);
        if (fileBytes == null || fileBytes.length == 0) {
          botSenderService.sendTextReply(chatId,
              i18nService.getTranslate("bot.translate.download.error"));
          return;
        }

        final String languageName = documentTranslationService.resolveLanguageName(languageCode);
        final String preparingText =
            i18nService.getTranslate("bot.translate.preparing", languageName);
        final Integer progressMessageId =
            botSenderService.sendTextAndGetMessageId(chatId, preparingText + " 0%");

        final TranslationResult result = documentTranslationService.translate(fileBytes, filename,
            languageCode, progress -> {
              if (progressMessageId != null) {
                botSenderService.editMessageText(chatId, progressMessageId,
                    preparingText + " " + progress + "%");
              }
            });
        if (result == null || result.content().length == 0) {
          botSenderService.sendTextReply(chatId, i18nService.getTranslate("bot.translate.failed"));
          return;
        }

        botSenderService.sendDocument(chatId, result.content(),
            buildFilename(filename, languageCode, result.extension()));
      } catch (final Exception ex) {
        log.error("Error processing {} command for chat {}", COMMAND, chatId, ex);
        botSenderService.sendTextReply(chatId,
            i18nService.getTranslate("bot.translate.error", ex.getMessage()));
      }
    });
    return null;
  }

  private String resolveLanguageCode(final String normalizedText) {
    final String command = BotUtils.normalizeCommand(normalizedText);
    if (StringUtils.isBlank(command) || !command.contains("-")) {
      return DEFAULT_LANGUAGE_CODE;
    }
    final String languageCode = command.substring(command.indexOf('-') + 1).trim();
    return StringUtils.isNotBlank(languageCode) ? languageCode : DEFAULT_LANGUAGE_CODE;
  }

  private String buildFilename(final String sourceFilename, final String languageCode,
      final String extension) {
    final String baseName = sourceFilename.contains(".")
        ? sourceFilename.substring(0, sourceFilename.lastIndexOf('.'))
        : sourceFilename;
    return baseName + "_" + languageCode.toLowerCase(Locale.ROOT) + "_"
        + System.currentTimeMillis() + "." + extension;
  }

  private IncomingMessage resolveDocumentMessage(final IncomingMessage message) {
    if (message.hasDocument()) {
      return message;
    }
    final IncomingMessage replyTo = message.getReplyToMessage();
    if (replyTo != null && replyTo.hasDocument()) {
      return replyTo;
    }
    return null;
  }
}
