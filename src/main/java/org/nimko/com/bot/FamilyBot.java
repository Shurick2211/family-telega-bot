package org.nimko.com.bot;

import static org.nimko.com.repository.ChatContextRepository.getTodayContext;
import static org.nimko.com.util.BotUtils.addTranscribedInContext;
import static org.nimko.com.util.TranscribedUtils.getTranscribed;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.bot.commands.CommandProcess;
import org.nimko.com.bot.dto.ReplyData;
import org.nimko.com.bot.messenger.IncomingMessage;
import org.nimko.com.bot.messenger.MediaFileService;
import org.nimko.com.bot.messenger.MessengerUser;
import org.nimko.com.bot.messenger.ReactionEvent;
import org.nimko.com.repository.ChatContextRepository;
import org.nimko.com.services.AudioConverter;
import org.nimko.com.services.I18nService;
import org.nimko.com.services.MediaDownloadService;
import org.nimko.com.services.TranslationContext;
import org.nimko.com.util.BotUtils;

/**
 * Messenger-agnostic bot core: dispatches incoming messages/reactions to {@link CommandProcess}
 * implementations and sends replies back via {@link BotSenderService}. Subclasses are responsible
 * only for translating a concrete messenger's native updates into {@link IncomingMessage}/
 * {@link ReactionEvent} and calling {@link #handleMessage}/{@link #handleReaction}.
 */
@Slf4j
public abstract class FamilyBot {

  protected final String botUsername;
  protected final AiChatService aiChatService;
  protected final AudioConverter audioConverter;
  protected final MediaDownloadService mediaDownloadService;
  protected final boolean needAutoTranscribe;
  protected final I18nService i18nService;
  protected final BotSenderService botSenderService;
  protected final List<CommandProcess> commandProcesses;
  protected final MediaFileService mediaFileService;
  protected final ChatContextRepository chatContextRepository;
  protected final ObjectMapper objectMapper;
  protected final long newsChatId;

  protected FamilyBot(final String botUsername, final AiChatService aiChatService,
      final AudioConverter audioConverter, final MediaDownloadService mediaDownloadService,
      final boolean needAutoTranscribe, final I18nService i18nService,
      final BotSenderService botSenderService, final List<CommandProcess> commandProcesses,
      final MediaFileService mediaFileService, final ChatContextRepository chatContextRepository,
      final ObjectMapper objectMapper, final long newsChatId) {
    this.botUsername = botUsername;
    this.aiChatService = aiChatService;
    this.audioConverter = audioConverter;
    this.mediaDownloadService = mediaDownloadService;
    this.needAutoTranscribe = needAutoTranscribe;
    this.i18nService = i18nService;
    this.botSenderService = botSenderService;
    this.commandProcesses = commandProcesses;
    this.mediaFileService = mediaFileService;
    this.chatContextRepository = chatContextRepository;
    this.objectMapper = objectMapper;
    this.newsChatId = newsChatId;
  }

  protected final Locale resolveLocale(final IncomingMessage message) {
    if (message == null) {
      return Locale.forLanguageTag("uk");
    }

    final String text = message.getText();
    final Long chatId = message.getChatId();

    String langCode = null;
    if (message.isGroupChat()) {
      langCode = BotUtils.detectGroupLanguage(text,
          getTodayContext(chatContextRepository, objectMapper, chatId, newsChatId));
    }

    if (langCode == null && message.getFrom() != null) {
      langCode = message.getFrom().getLanguageCode();
    }

    return BotUtils.resolveLocale(langCode);
  }

  protected final Locale resolveLocale(final ReactionEvent reactionEvent) {
    final MessengerUser user = reactionEvent != null ? reactionEvent.getUser() : null;
    return BotUtils.resolveLocale(user != null ? user.getLanguageCode() : null);
  }

  protected final void handleMessage(final IncomingMessage message) {
    if (message == null) {
      return;
    }

    final String text = message.getText();
    final boolean hasPhoto = message.hasPhoto();
    final boolean hasVoice = message.hasVoice();
    final boolean hasAudio = message.hasAudio();
    final boolean hasVideoNote = message.hasVideoNote();
    final boolean hasVideo = message.hasVideo();
    final boolean hasDocument = message.hasDocument();
    final boolean hasAudioMessage = hasVoice || hasAudio;
    final Long chatId = message.getChatId();
    final boolean groupChat = message.isGroupChat();
    final int messageId = message.getMessageId();

    final byte[] imageBytes = hasPhoto ? mediaFileService.downloadBestPhoto(message) : null;
    final byte[] downloadedAudioBytes =
        hasAudioMessage ? mediaFileService.downloadAudioMessage(message) : null;
    final byte[] downloadedVideoBytes =
        (hasVideoNote || hasVideo || (hasDocument && mediaFileService.isMediaDocument(message)))
            ? mediaFileService.downloadAudioMessage(message) : null;

    byte[] rawAudioBytes;
    if (hasVoice && downloadedAudioBytes != null && downloadedAudioBytes.length > 0) {
      log.info("Converting OGG voice note to MP3...");
      try {
        rawAudioBytes = audioConverter.convertOggToMp3(downloadedAudioBytes);
      } catch (final Exception ex) {
        log.error("Failed to convert OGG voice note to MP3, skipping conversion result", ex);
        rawAudioBytes = null;
      }
    } else {
      rawAudioBytes = downloadedAudioBytes;
    }

    byte[] extractedAudioFromVideoBytes;
    if ((hasVideoNote || hasVideo) && downloadedVideoBytes != null
        && downloadedVideoBytes.length > 0) {
      log.info("Extracting audio track from video...");
      try {
        extractedAudioFromVideoBytes = audioConverter.extractAudioFromVideo(downloadedVideoBytes);
      } catch (final Exception ex) {
        log.error("Failed to extract audio track from video, skipping extraction result", ex);
        extractedAudioFromVideoBytes = null;
      }
    } else {
      extractedAudioFromVideoBytes = null;
    }

    final String normalizedText = StringUtils.isNotBlank(text) ? text.trim() : "";

    log.info("Received message: author={} {}", message.getFrom().getId(),
        message.getFrom().getUsername());

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
          mediaDownloadService.submitDownload(chatId, foundUrl, TranslationContext.getLocale());
          botSenderService.sendTextReply(chatId, i18nService.getTranslate("bot.media.downloading"));
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

        addTranscribedInContext(message.getFrom().getUsername(), message.getFrom().getPersonName(),
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

        addTranscribedInContext(message.getFrom().getUsername(), message.getFrom().getPersonName(),
            "[audio] " + transcribed, chatId, messageId, chatContextRepository, groupChat);
      }
      return;
    }

    if (groupChat) {
      if (StringUtils.isBlank(text)) {
        log.info("Received empty text message in group chat");
        return;
      }

      if (!BotUtils.isAddressedToBot(text, botUsername) && !message.isReplyToBot(botUsername)
          && !isCommand) {
        addTranscribedInContext(message.getFrom().getUsername(), message.getFrom().getPersonName(),
            text, chatId, messageId, chatContextRepository, groupChat);
        log.info("Saved context in group chat");
        return;
      }
    }

    final byte[] finalRawAudioBytes = rawAudioBytes;
    final byte[] finalExtractedAudioFromVideoBytes = extractedAudioFromVideoBytes;
    final ReplyData response = commandProcesses.stream()
        .filter(c -> c.isCommand(BotUtils.normalizeCommand(normalizedText)))
        .findFirst().map(c -> c.execute(normalizedText, hasPhoto, imageBytes, message, chatId,
            hasVoice, finalRawAudioBytes, finalExtractedAudioFromVideoBytes, groupChat, messageId))
        .orElse(null);

    if (response != null) {
      if (response.newsResponse()) {
        botSenderService.sendNewsReply(chatId, response.text(), hasPhoto ? imageBytes : null);
      } else {
        botSenderService.sendReply(chatId, response.text(), hasPhoto ? imageBytes : null);
      }
    }
  }

  protected final void handleReaction(final ReactionEvent reactionEvent) {
    commandProcesses.forEach(c -> c.handleReaction(reactionEvent));
  }

  private boolean isNoNews(final Long chatId) {
    return chatId != newsChatId;
  }

  @PreDestroy
  public void shutdown() {
    mediaDownloadService.shutdown();
  }

}
