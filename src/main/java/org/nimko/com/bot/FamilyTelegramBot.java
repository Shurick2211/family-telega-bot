package org.nimko.com.bot;

import static org.nimko.com.util.BotUtils.addTranscribedInContext;
import static org.nimko.com.util.BotUtils.hasAudioVideo;
import static org.nimko.com.util.TranscribedUtils.getTranscribed;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.config.TelegramBotProperties;
import org.nimko.com.services.AudioConverter;
import org.nimko.com.services.DocxGeneratorService;
import org.nimko.com.services.MediaDownloadService;
import org.nimko.com.services.TranslationService;
import org.nimko.com.services.TranslationContext;
import java.util.Locale;
import org.nimko.com.util.BotUtils;
import org.nimko.com.util.BotUtils.ReplyPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.client.RestClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

public class FamilyTelegramBot implements LongPollingUpdateConsumer {

  private static final Logger log = LoggerFactory.getLogger(FamilyTelegramBot.class);

  private final String botToken;
  private final String botUsername;
  private final String telegramApiBaseUrl;
  private final AiChatService aiChatService;
  private final RestClient telegramClient;

  private final Map<Long, List<String>> chatContext = new ConcurrentHashMap<>();
  private final AudioConverter audioConverter;
  private final DocxGeneratorService docxGeneratorService;
  private final MediaDownloadService mediaDownloadService;
  private final boolean needAutoTranscribe;
  private final TranslationService translationService;
  private final BotSenderService botSenderService;

  private final long newsChatId;

  public FamilyTelegramBot(
      final TelegramBotProperties telegramProperties,
      final AiChatService aiChatService, final AudioConverter audioConverter,
      final DocxGeneratorService docxGeneratorService,
      final boolean needAutoTranscribe, final long newsChatId, final String downloaderEndpoint,
      final TranslationService translationService,
      final BotSenderService botSenderService) {
    this.botToken = telegramProperties.token();
    this.botUsername = telegramProperties.username();
    this.telegramApiBaseUrl = StringUtils.isNotBlank(telegramProperties.apiBaseUrl())
        ? telegramProperties.apiBaseUrl()
        : "https://api.telegram.org";
    this.aiChatService = aiChatService;
    this.audioConverter = audioConverter;
    this.docxGeneratorService = docxGeneratorService;
    this.needAutoTranscribe = needAutoTranscribe;
    this.newsChatId = newsChatId;
    this.translationService = translationService;
    this.botSenderService = botSenderService;
    this.mediaDownloadService = new MediaDownloadService(botSenderService, downloaderEndpoint);
    this.telegramClient = RestClient.builder()
        .baseUrl(telegramApiBaseUrl)
        .build();
  }

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

    if (update.hasMessage() && update.getMessage() != null) {
      final var message = update.getMessage();
      final String text = BotUtils.resolveIncomingText(message);
      final Long chatId = message.getChatId();

      String langCode = null;
      if (BotUtils.isGroupChat(message)) {
        final List<String> history = chatContext.get(chatId);
        langCode = BotUtils.detectGroupLanguage(text, history);
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
    final boolean isCommand = text != null && text.startsWith("/");
    final int messageId = message.getMessageId();

    final byte[] imageBytes = hasPhoto ? downloadBestPhoto(message) : null;
    final byte[] downloadedAudioBytes = hasAudioMessage ? downloadAudioMessage(message) : null;
    final byte[] downloadedVideoBytes = (hasVideoNote || hasVideo || (hasDocument && isMediaDocument(message))) ? downloadAudioMessage(message) : null;

    final byte[] rawAudioBytes;
    if (hasVoice && downloadedAudioBytes != null && downloadedAudioBytes.length > 0) {
      log.info("Converting OGG voice note to MP3...");
      rawAudioBytes = audioConverter.convertOggToMp3(downloadedAudioBytes);
    } else {
      rawAudioBytes = downloadedAudioBytes;
    }

    final byte[] extractedAudioFromVideoBytes;
    if ((hasVideoNote || hasVideo) && downloadedVideoBytes != null && downloadedVideoBytes.length > 0) {
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

    if (hasPhoto && (imageBytes == null || imageBytes.length == 0) && !isNews(chatId)) {
      log.warn("Failed to download the image.");
      return;
    }
    if (hasAudioMessage && (rawAudioBytes == null || rawAudioBytes.length == 0) && !isNews(chatId)) {
      log.warn("Failed to download or convert audio.");
      return;
    }
    
    if ((hasVideoNote || hasVideo) && (extractedAudioFromVideoBytes == null
        || extractedAudioFromVideoBytes.length == 0) && !isNews(chatId)) {
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
              translationService.getTranslate("bot.media.downloading"));
        } catch (final Exception ex) {
          log.error("Error downloading media from URL: {}", foundUrl, ex);
        }
        return;
      }
    }

    if ((hasVideoNote || hasVideo) && !isCommand && !isNews(chatId)) {
      if (groupChat && needAutoTranscribe) {
        final String transcribed = getTranscribed(true, message, extractedAudioFromVideoBytes,
            aiChatService);
        if (StringUtils.isBlank(transcribed)) {
          log.warn("Failed to transcribe the video audio.");
          return;
        }

        addTranscribedInContext(BotUtils.getSenderName(message.getFrom()), BotUtils.getSenderName(message.getFrom()),
            "[video] " + transcribed, chatId, messageId,chatContext);
      }
      return;
    }

    if (hasAudioMessage && !isCommand && !isNews(chatId)) {
      if (groupChat && needAutoTranscribe) {
        final String transcribed = getTranscribed(hasVoice, message, rawAudioBytes, aiChatService);
        if (StringUtils.isBlank(transcribed)) {
          log.warn("Failed to transcribe the audio.");
          return;
        }

        addTranscribedInContext(BotUtils.getSenderName(message.getFrom()), BotUtils.getSenderName(message.getFrom()),
            "[audio] " + transcribed, chatId, messageId,chatContext);
      }
      return;
    }

    if (groupChat) {
      if (StringUtils.isBlank(text)) {
        log.info("Received empty text message in group chat");
        return;
      }

      if (!BotUtils.isAddressedToBot(text, botUsername) && !BotUtils.isReplyToBot(message, botUsername) && !isCommand) {
        addTranscribedInContext(BotUtils.getSenderName(message.getFrom()), BotUtils.getSenderName(message.getFrom()),
            text, chatId, messageId, chatContext);
        log.info("Saved context in group chat");
        return;
      }
    }

    final ReplyData response = processByBot(normalizedText, hasPhoto, imageBytes, message, chatId,
        hasVoice, rawAudioBytes, extractedAudioFromVideoBytes, groupChat, messageId);

    if (response != null) {
      if (response.newsResponse()) {
        botSenderService.sendNewsReply(chatId, response.text(), hasPhoto ? imageBytes : null);
      } else {
        botSenderService.sendReply(chatId, response.text(), hasPhoto ? imageBytes : null);
      }
    }
  }

  @Nullable
  private ReplyData processByBot(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final Message message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId) {
    return switch (BotUtils.normalizeCommand(normalizedText)) {
      case "/start" -> new ReplyData(translationService.getTranslate("bot.start"), false);
      case "/hello" -> new ReplyData(translationService.getTranslate("bot.hello"), false);
      case "/news" -> newsAction(normalizedText, hasPhoto, imageBytes);
      case "/articles" -> {
        articlesAction(message, normalizedText, chatId);
        yield null;
      }
      case "/text" -> {
        handleTextCommandFull(message, hasVoice, rawAudioBytes, extractedAudioFromVideoBytes,
            chatId);
        yield null;
      }
      case "/audio" -> {
        handleAudioCommandFull(message, extractedAudioFromVideoBytes, chatId);
        yield null;
      }
      case "/context" -> {
        final var contextList = chatContext.get(chatId);
        yield new ReplyData(
            contextList != null ? String.join("\n", contextList)
                : translationService.getTranslate("bot.context.empty"), false);
      }
      case "/help" -> new ReplyData(translationService.getTranslate("bot.help"), false);
      default -> justMessaging(normalizedText, hasPhoto, chatId, groupChat, imageBytes,
          BotUtils.getSenderName(message.getFrom()), messageId);
    };
  }

  private void handleTextCommandFull(final Message message, final boolean hasVoice,
      final byte[] rawAudioBytes, final byte[] extractedAudioFromVideoBytes, final Long chatId) {
    Message targetMessage = message;
    byte[] audioToUse = null;
    boolean isVoice = hasVoice;

    if (hasAudioVideo(message)) {
      if (message.hasVideoNote() || message.hasVideo()) {
        audioToUse = extractedAudioFromVideoBytes;
      } else if (message.hasVoice() || message.hasAudio()) {
        audioToUse = rawAudioBytes;
        isVoice = message.hasVoice();
      }
    } else if (message.getReplyToMessage() != null) {
      final Message replyTo = message.getReplyToMessage();
      if (hasAudioVideo(replyTo) || (replyTo.hasDocument() && isMediaDocument(replyTo))) {
        targetMessage = replyTo;
        final var replyContextOp = chatContext.get(chatId).stream()
            .filter(j -> (int) JSON.parseObject(j).getInteger("messageId")
                == replyTo.getMessageId()).findFirst();
        if (replyContextOp.isPresent()) {
          final var replyContext = JSON.parseObject(replyContextOp.get());
          final var replyText = replyContext.getString("text");
          if (StringUtils.isNotBlank(replyText)) {
            botSenderService.sendTextReply(chatId, replyText);
            return;
          }
        }

        final byte[] replyAudioBytes;
        if (replyTo.hasVoice() || replyTo.hasAudio() || (replyTo.hasDocument() && isMediaDocument(replyTo))) {
          replyAudioBytes = downloadAudioMessage(replyTo);
          isVoice = replyTo.hasVoice();
        } else {
          replyAudioBytes = downloadAudioMessage(replyTo);
        }
        
        if (replyAudioBytes != null && replyAudioBytes.length > 0) {
          if (replyTo.hasVoice()) {
            audioToUse = audioConverter.convertOggToMp3(replyAudioBytes);
          } else if (replyTo.hasVideoNote() || replyTo.hasVideo() || (replyTo.hasDocument() && isMediaDocument(replyTo))) {
            audioToUse = audioConverter.extractAudioFromVideo(replyAudioBytes);
          } else {
            audioToUse = replyAudioBytes;
          }
        }
      }
    }

    if (audioToUse == null || audioToUse.length == 0) {
      botSenderService.sendTextReply(chatId, translationService.getTranslate("bot.text.reply.prompt"));
      return;
    }

    try {
      log.info("Transcribing audio...");
      final String transcribed = getTranscribed(isVoice, targetMessage, audioToUse, aiChatService);

      if (StringUtils.isBlank(transcribed)) {
        log.warn("Failed to transcribe the audio.");
        botSenderService.sendTextReply(chatId, translationService.getTranslate("bot.text.failed"));
        return;
      }

      botSenderService.sendTextReply(chatId, transcribed);
    } catch (final Exception ex) {
      log.error("Error processing /text command for chat {}", chatId, ex);
      botSenderService.sendTextReply(chatId, translationService.getTranslate("bot.text.error", ex.getMessage()));
    }
  }

  private void handleAudioCommandFull(final Message message, final byte[] extractedAudioFromVideoBytes, final Long chatId) {
    byte[] audioToSend = null;

    if (message.hasVideoNote() || message.hasVideo()) {
      audioToSend = extractedAudioFromVideoBytes;
    }
    else if (message.getReplyToMessage() != null && (message.getReplyToMessage().hasVideoNote() || message.getReplyToMessage().hasVideo() || 
             (message.getReplyToMessage().hasDocument() && isMediaDocument(message.getReplyToMessage())))) {
      final Message replyTo = message.getReplyToMessage();
      final byte[] replyVideoBytes = downloadAudioMessage(replyTo);
      if (replyVideoBytes != null && replyVideoBytes.length > 0) {
        audioToSend = audioConverter.extractAudioFromVideo(replyVideoBytes);
      }
    }

    if (audioToSend == null || audioToSend.length == 0) {
      botSenderService.sendTextReply(chatId, translationService.getTranslate("bot.audio.reply.prompt"));
      return;
    }

    try {
      log.info("Sending extracted audio as MP3...");
      botSenderService.sendAudioFile(chatId, audioToSend, "audio.mp3");
    } catch (final Exception ex) {
      log.error("Error processing /audio command for chat {}", chatId, ex);
      botSenderService.sendTextReply(chatId, translationService.getTranslate("bot.audio.error", ex.getMessage()));
    }
  }

  private ReplyData justMessaging(final String normalizedText, final boolean hasPhoto,
      final Long chatId, final boolean groupChat, final byte[] imageBytes,
      final String authorUsername, final int messageId) {

    if (StringUtils.isNotBlank(normalizedText) && normalizedText.startsWith("/")) {
      return new ReplyData(translationService.getTranslate("bot.command.unknown", normalizedText), false);
    }

    final List<String> currentHistory = chatContext.getOrDefault(chatId, Collections.emptyList());
    final String prompt = groupChat
        ? BotUtils.stripBotPrefix(normalizedText, botUsername, currentHistory, groupChat)
        : normalizedText;

    if (groupChat && StringUtils.isNotBlank(normalizedText)) {
      addTranscribedInContext(authorUsername, authorUsername,
          BotUtils.stripTextPrefix(normalizedText), chatId, messageId, chatContext);
    }

    if (hasPhoto) {
      final String imagePrompt =
          StringUtils.isNotBlank(normalizedText) ? prompt : "Describe the image in detail.";
      log.info("Image prompt: {}", imagePrompt);
      return chatId == newsChatId && !groupChat
          ? new ReplyData(
          aiChatService.askNewsWithImage(BotUtils.prepareNewsPrompt(imagePrompt), imageBytes,
              "image/jpeg"), true)
          : new ReplyData(aiChatService.askWithImage(imagePrompt, imageBytes, "image/jpeg"), false);
    }

    if (StringUtils.isBlank(prompt)) {
      return null;
    }

    log.debug("Prompt sent to AI: {}", prompt);
    final var result = isNews(chatId) && !groupChat ?
        new ReplyData(aiChatService.askNews(BotUtils.prepareNewsPrompt(prompt)), true)
        : new ReplyData(aiChatService.ask(prompt), false);

    if (groupChat && result.text() != null) {
      addTranscribedInContext(botUsername, "Bot", result.text(), chatId, messageId, chatContext);
    }

    return result;
  }

  private boolean isNews(final Long chatId) {
    return chatId == newsChatId;
  }

  private ReplyData newsAction(final String normalizedText, final boolean hasPhoto,
      final byte[] imageBytes) {
    final String promptNews = BotUtils.extractCommandPayload(normalizedText);
    final String preparedPrompt = BotUtils.prepareNewsPrompt(StringUtils.isNotBlank(promptNews)
        ? promptNews
        : "Перепиши новину за інформацією отримавши інформацію з прикріпленого посилання.");

    return hasPhoto
        ? new ReplyData(aiChatService.askNewsWithImage(preparedPrompt, imageBytes, "image/jpeg"),
        true)
        : new ReplyData(aiChatService.askNews(preparedPrompt), true);
  }

  private void articlesAction(final Message message, final String normalizedText, final Long chatId) {
    String promptArticles = BotUtils.extractCommandPayload(normalizedText);

    if (message.getReplyToMessage() != null) {
      final String replyText = BotUtils.resolveIncomingText(message.getReplyToMessage());
      if (StringUtils.isNotBlank(replyText)) {
        if (StringUtils.isNotBlank(promptArticles)) {
          promptArticles = promptArticles + "\n\nДжерело/Матеріал:\n" + replyText;
        } else {
          promptArticles = replyText;
        }
      }
    }

    final String preparedPrompt = BotUtils.prepareArticlesPrompt(StringUtils.isNotBlank(promptArticles)
        ? promptArticles
        : "Напиши розгорнуту статтю за інформацією отримавши інформацію з прикріпленого посилання.");

    try {
      botSenderService.sendTextReply(chatId, "Генерую статтю, зачекайте будь ласка...");

      final String generatedArticle = aiChatService.askArticle(preparedPrompt);
      if (StringUtils.isBlank(generatedArticle) || generatedArticle.startsWith("AI provider")) {
        botSenderService.sendTextReply(chatId, "Не вдалося згенерувати статтю: " + generatedArticle);
        return;
      }

      log.info("Generating DOCX document from generated article...");
      final byte[] docxBytes = docxGeneratorService.generateDocxFromMarkdown(generatedArticle);
      if (docxBytes == null || docxBytes.length == 0) {
        botSenderService.sendTextReply(chatId, "Помилка при створенні .docx файлу.");
        return;
      }

      final String filename = "article_" + System.currentTimeMillis() + ".docx";
      botSenderService.sendDocument(chatId, docxBytes, filename);
    } catch (final Exception ex) {
      log.error("Error processing /articles command for chat {}", chatId, ex);
      botSenderService.sendTextReply(chatId, "Сталася помилка при обробці команди: " + ex.getMessage());
    }
  }

  private boolean isMediaDocument(final Message message) {
    if (!message.hasDocument() || message.getDocument() == null) {
      return false;
    }
    final String mimeType = message.getDocument().getMimeType();
    return mimeType != null && (mimeType.startsWith("audio/") || mimeType.startsWith("video/"));
  }

  private byte[] downloadBestPhoto(final Message message) {
    if (!message.hasPhoto() || message.getPhoto() == null || message.getPhoto().isEmpty()) {
      return null;
    }

    try {
      final var bestPhoto = message.getPhoto().get(message.getPhoto().size() - 1);
      if (bestPhoto == null || StringUtils.isBlank(bestPhoto.getFileId())) {
        return null;
      }

      final TelegramFileResponse response = telegramClient.get()
          .uri(uriBuilder -> uriBuilder
              .path("/bot{token}/getFile")
              .queryParam("file_id", bestPhoto.getFileId())
              .build(botToken))
          .retrieve()
          .body(TelegramFileResponse.class);

      if (response == null || response.result() == null || StringUtils.isBlank(
          response.result().filePath())) {
        return null;
      }

      final String fileUrl = BotUtils.buildTelegramFileUrl(telegramApiBaseUrl, botToken,
          response.result().filePath());
      return telegramClient.get()
          .uri(URI.create(fileUrl))
          .retrieve()
          .body(byte[].class);
    } catch (final RuntimeException ex) {
      log.error("Failed to download photo from Telegram for chat {}", message.getChatId(), ex);
      return null;
    }
  }

  private byte[] downloadAudioMessage(final Message message) {
    String fileId = null;

    if (message.hasVoice() && message.getVoice() != null) {
      fileId = message.getVoice().getFileId();
    } else if (message.hasAudio() && message.getAudio() != null) {
      fileId = message.getAudio().getFileId();
    } else if (message.hasVideoNote() && message.getVideoNote() != null) {
      fileId = message.getVideoNote().getFileId();
    } else if (message.hasVideo() && message.getVideo() != null) {
      fileId = message.getVideo().getFileId();
    } else if (message.hasDocument() && message.getDocument() != null) {
      fileId = message.getDocument().getFileId();
    }

    final String resolvedFileId = fileId;
    if (StringUtils.isBlank(resolvedFileId)) {
      return null;
    }

    try {
      final TelegramFileResponse response = telegramClient.get()
          .uri(uriBuilder -> uriBuilder
              .path("/bot{token}/getFile")
              .queryParam("file_id", resolvedFileId)
              .build(botToken))
          .retrieve()
          .body(TelegramFileResponse.class);

      if (response == null || response.result() == null || StringUtils.isBlank(
          response.result().filePath())) {
        return null;
      }

      final String fileUrl = BotUtils.buildTelegramFileUrl(telegramApiBaseUrl, botToken,
          response.result().filePath());
      return telegramClient.get()
          .uri(URI.create(fileUrl))
          .retrieve()
          .body(byte[].class);
    } catch (final Exception ex) {
      log.error("Failed to download media from Telegram for chat {}", message.getChatId(), ex);
      return null;
    }
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
      botSenderService.answerCallbackQuery(callbackQuery.getId(), translationService.getTranslate("bot.callback.image.unavailable"));
      return;
    }

    final Long chatId = callbackQuery.getMessage().getChatId();
    final boolean sent = botSenderService.sendCopiedImage(chatId, payload);
    if (sent) {
      BotUtils.removeCopyImagePayload(token);
      botSenderService.answerCallbackQuery(callbackQuery.getId(), null);
    } else {
      botSenderService.answerCallbackQuery(callbackQuery.getId(), translationService.getTranslate("bot.callback.image.failed"));
    }
  }

  @PreDestroy
  private void destroy() {
    mediaDownloadService.shutdown();
  }

  @Scheduled(cron = "0 0 2 * * *")
  private void clearChatContext() {
    chatContext.clear();
  }

  private record TelegramFileResponse(boolean ok, TelegramFile result) {

  }

  private record ReplyData(String text, boolean newsResponse) {

  }

  private record TelegramFile(@JsonProperty("file_path") String filePath) {

  }
}