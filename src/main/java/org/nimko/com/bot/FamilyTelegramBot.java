package org.nimko.com.bot;

import static org.nimko.com.repository.ChatContextRepository.getTodayContext;
import static org.nimko.com.util.BotUtils.addTranscribedInContext;
import static org.nimko.com.util.TranscribedUtils.getTranscribed;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.bot.commands.CommandProcess;
import org.nimko.com.repository.ChatContextRepository;
import org.nimko.com.services.AudioConverter;
import org.nimko.com.services.MediaDownloadService;
import org.nimko.com.services.TelegramFileService;
import org.nimko.com.services.I18nService;
import org.nimko.com.services.TranslationContext;
import java.util.Locale;
import org.nimko.com.util.BotUtils;
import org.nimko.com.util.BotUtils.ReplyPayload;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.reactions.MessageReactionUpdated;

@Slf4j
@RequiredArgsConstructor
public class FamilyTelegramBot implements LongPollingUpdateConsumer {

  private final String botUsername;
  private final AiChatService aiChatService;

  private final AudioConverter audioConverter;
  private final MediaDownloadService mediaDownloadService;
  private final boolean needAutoTranscribe;
  private final I18nService i18nService;
  private final BotSenderService botSenderService;
  private final List<CommandProcess> commandProcesses;
  private final TelegramFileService telegramFileService;
  private final ChatContextRepository chatContextRepository;
  private final ObjectMapper objectMapper;

  private final long newsChatId;


  @Override
  public void consume(final List<Update> updates) {
    for (final Update update : updates) {
      if (update == null) {
        continue;
      }

      try {
        final Locale resolvedLocale = resolveLocaleFromUpdate(update);
        TranslationContext.setLocale(resolvedLocale);

        if (update.hasCallbackQuery()) {
          handleCallbackQuery(update.getCallbackQuery());
          continue;
        }

        if (update.getMessageReaction() != null) {
          handleMessageReaction(update.getMessageReaction());
          continue;
        }

        if (!update.hasMessage()) {
          continue;
        }

        final var message = update.getMessage();
        if (!BotUtils.hasUserContent(message)) {
          continue;
        }

        log.info("Received Telegram message: chatId={} text={} hasPhoto={}",
            message.getChatId(), message.getText(), message.hasPhoto());
        handleUpdate(update);
      } finally {
        TranslationContext.clear();
      }
    }
  }

  private Locale resolveLocaleFromUpdate(final Update update) {
    if (update == null) {
      return Locale.forLanguageTag("uk");
    }

    if (update.hasCallbackQuery() && update.getCallbackQuery() != null) {
      final var from = update.getCallbackQuery().getFrom();
      final String langCode = from != null ? from.getLanguageCode() : null;
      return BotUtils.resolveLocale(langCode);
    }

    if (update.getMessageReaction() != null) {
      final var from = update.getMessageReaction().getUser();
      final String langCode = from != null ? from.getLanguageCode() : null;
      return BotUtils.resolveLocale(langCode);
    }

    if (update.hasMessage() && update.getMessage() != null) {
      final var message = update.getMessage();
      final String text = BotUtils.resolveIncomingText(message);
      final Long chatId = message.getChatId();

      String langCode = null;
      if (BotUtils.isGroupChat(message)) {
        langCode = BotUtils.detectGroupLanguage(text,
            getTodayContext(chatContextRepository, objectMapper, chatId, newsChatId));
      }

      if (langCode == null && message.getFrom() != null) {
        langCode = message.getFrom().getLanguageCode();
      }

      return BotUtils.resolveLocale(langCode);
    }

    return Locale.forLanguageTag("uk");
  }

  private void handleUpdate(final Update update) {
    if (update == null || !update.hasMessage()) {
      return;
    }

    final var message = update.getMessage();
    final String text = BotUtils.resolveIncomingText(message);
    final boolean hasPhoto = message.hasPhoto();
    final boolean hasVoice = message.hasVoice();
    final boolean hasAudio = message.hasAudio();
    final boolean hasVideoNote = message.hasVideoNote();
    final boolean hasVideo = message.hasVideo();
    final boolean hasDocument = message.hasDocument();
    final boolean hasAudioMessage = hasVoice || hasAudio;
    final Long chatId = message.getChatId();
    final boolean groupChat = BotUtils.isGroupChat(message);
    final int messageId = message.getMessageId();

    final byte[] imageBytes = hasPhoto ? telegramFileService.downloadBestPhoto(message) : null;
    final byte[] downloadedAudioBytes =
        hasAudioMessage ? telegramFileService.downloadAudioMessage(message) : null;
    final byte[] downloadedVideoBytes =
        (hasVideoNote || hasVideo || (hasDocument && telegramFileService.isMediaDocument(message)))
            ? telegramFileService.downloadAudioMessage(message) : null;

    final byte[] rawAudioBytes;
    if (hasVoice && downloadedAudioBytes != null && downloadedAudioBytes.length > 0) {
      log.info("Converting OGG voice note to MP3...");
      rawAudioBytes = audioConverter.convertOggToMp3(downloadedAudioBytes);
    } else {
      rawAudioBytes = downloadedAudioBytes;
    }

    final byte[] extractedAudioFromVideoBytes;
    if ((hasVideoNote || hasVideo) && downloadedVideoBytes != null
        && downloadedVideoBytes.length > 0) {
      log.info("Extracting audio track from video...");
      extractedAudioFromVideoBytes = audioConverter.extractAudioFromVideo(downloadedVideoBytes);
    } else {
      extractedAudioFromVideoBytes = null;
    }

    final String normalizedText = StringUtils.isNotBlank(text) ? text.trim() : "";

    log.info("Received Telegram message: author={} {}", message.getFrom().getId(),
        message.getFrom().getUserName());

    if (!hasPhoto && !hasAudioMessage && !hasVideoNote && !hasVideo && StringUtils.isBlank(text)) {
      log.info("Received empty message");
      return;
    }

    if (hasPhoto && (imageBytes == null || imageBytes.length == 0) && isNoNews(chatId)) {
      log.warn("Failed to download the image.");
      return;
    }
    if (hasAudioMessage && (rawAudioBytes == null || rawAudioBytes.length == 0) && isNoNews(
        chatId)) {
      log.warn("Failed to download or convert audio.");
      return;
    }

    if ((hasVideoNote || hasVideo) && (extractedAudioFromVideoBytes == null
        || extractedAudioFromVideoBytes.length == 0) && isNoNews(chatId)) {
      log.warn("Failed to extract audio from video.");
      return;
    }

    if (StringUtils.isNotBlank(normalizedText) && BotUtils.containsMediaUrl(normalizedText)) {
      final String foundUrl = BotUtils.extractFirstUrl(normalizedText);
      if (StringUtils.isNotBlank(foundUrl)) {
        try {
          mediaDownloadService.submitDownload(message.getChatId(), foundUrl,
              TranslationContext.getLocale());
          botSenderService.sendTextReply(message.getChatId(),
              i18nService.getTranslate("bot.media.downloading"));
        } catch (final Exception ex) {
          log.error("Error downloading media from URL: {}", foundUrl, ex);
        }
        return;
      }
    }

    final boolean isCommand = text != null && text.startsWith("/");
    if ((hasVideoNote || hasVideo) && !isCommand && isNoNews(chatId)) {
      if (needAutoTranscribe) {
        final String transcribed = getTranscribed(true, message, extractedAudioFromVideoBytes,
            aiChatService);
        if (StringUtils.isBlank(transcribed)) {
          log.warn("Failed to transcribe the video audio.");
          return;
        }

        addTranscribedInContext(BotUtils.getSenderName(message.getFrom()),
            BotUtils.getSenderName(message.getFrom()),
            "[video] " + transcribed, chatId, messageId, chatContextRepository, groupChat);
      }
      return;
    }

    if (hasAudioMessage && !isCommand && isNoNews(chatId)) {
      if (needAutoTranscribe) {
        final String transcribed = getTranscribed(hasVoice, message, rawAudioBytes, aiChatService);
        if (StringUtils.isBlank(transcribed)) {
          log.warn("Failed to transcribe the audio.");
          return;
        }

        addTranscribedInContext(BotUtils.getSenderName(message.getFrom()),
            BotUtils.getSenderName(message.getFrom()),
            "[audio] " + transcribed, chatId, messageId, chatContextRepository, groupChat);
      }
      return;
    }

    if (groupChat) {
      if (StringUtils.isBlank(text)) {
        log.info("Received empty text message in group chat");
        return;
      }

      if (!BotUtils.isAddressedToBot(text, botUsername) && !BotUtils.isReplyToBot(message,
          botUsername) && !isCommand) {
        addTranscribedInContext(BotUtils.getSenderName(message.getFrom()),
            BotUtils.getSenderName(message.getFrom()),
            text, chatId, messageId, chatContextRepository, groupChat);
        log.info("Saved context in group chat");
        return;
      }
    }

    final ReplyData response = commandProcesses.stream()
        .filter(c -> c.isCommand(BotUtils.normalizeCommand(normalizedText)))
        .findFirst().map(c -> c.execute(normalizedText, hasPhoto, imageBytes, message, chatId,
            hasVoice, rawAudioBytes, extractedAudioFromVideoBytes, groupChat, messageId))
        .orElse(null);

    if (response != null) {
      if (response.newsResponse()) {
        botSenderService.sendNewsReply(chatId, response.text(), hasPhoto ? imageBytes : null);
      } else {
        botSenderService.sendReply(chatId, response.text(), hasPhoto ? imageBytes : null);
      }
    }
  }

  private boolean isNoNews(final Long chatId) {
    return chatId != newsChatId;
  }

  private void handleMessageReaction(final MessageReactionUpdated messageReaction) {
    commandProcesses.forEach(c -> c.handleReaction(messageReaction));
  }

  private void handleCallbackQuery(final CallbackQuery callbackQuery) {
    if (callbackQuery == null || StringUtils.isBlank(callbackQuery.getData())
        || callbackQuery.getMessage() == null) {
      return;
    }

    final String data = callbackQuery.getData();
    if (!data.startsWith(BotSenderService.COPY_IMG_CALLBACK_PREFIX)) {
      return;
    }

    final String token = data.substring(BotSenderService.COPY_IMG_CALLBACK_PREFIX.length());
    final ReplyPayload payload = BotUtils.getCopyImagePayload(token);
    if (payload == null) {
      botSenderService.answerCallbackQuery(callbackQuery.getId(),
          i18nService.getTranslate("bot.callback.image.unavailable"));
      return;
    }

    final Long chatId = callbackQuery.getMessage().getChatId();
    final boolean sent = botSenderService.sendCopiedImage(chatId, payload);
    if (sent) {
      BotUtils.removeCopyImagePayload(token);
      botSenderService.answerCallbackQuery(callbackQuery.getId(), null);
    } else {
      botSenderService.answerCallbackQuery(callbackQuery.getId(),
          i18nService.getTranslate("bot.callback.image.failed"));
    }
  }

  @PreDestroy
  private void destroy() {
    mediaDownloadService.shutdown();
  }

  public record ReplyData(String text, boolean newsResponse) {

  }
}
