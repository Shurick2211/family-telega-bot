package org.nimko.com.bot;

import static org.nimko.com.util.BotUtils.addTranscribedInContext;
import static org.nimko.com.util.TranscribedUtils.getTranscribed;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.config.TelegramBotProperties;
import org.nimko.com.services.AudioConverter;
import org.nimko.com.services.MediaDownloadService;
import org.nimko.com.util.BotUtils;
import org.nimko.com.util.BotUtils.ReplyPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

public class HelloHelpTelegramBot implements LongPollingUpdateConsumer {

  private static final Logger log = LoggerFactory.getLogger(HelloHelpTelegramBot.class);
  private static final String COPY_IMG_CALLBACK_PREFIX = "copy_img:";
  private static final String TELEGRAM_PARSE_MODE = "Markdown";


  private final String botToken;
  private final String botUsername;
  private final String telegramApiBaseUrl;
  private final AiChatService aiChatService;
  private final RestClient telegramClient;

  private final Map<Long, List<String>> chatContext = new ConcurrentHashMap<>();
  private final AudioConverter audioConverter;
  private final MediaDownloadService mediaDownloadService;
  private final boolean needAutoTranscribe;

  private final long newsChatId;

  public HelloHelpTelegramBot(
      final TelegramBotProperties telegramProperties,
      final AiChatService aiChatService, final AudioConverter audioConverter,
      final boolean needAutoTranscribe, final long newsChatId, final String downloaderEndpoint) {
    this.botToken = telegramProperties.token();
    this.botUsername = telegramProperties.username();
    this.telegramApiBaseUrl = StringUtils.hasText(telegramProperties.apiBaseUrl())
        ? telegramProperties.apiBaseUrl()
        : "https://api.telegram.org";
    this.aiChatService = aiChatService;
    this.audioConverter = audioConverter;
    this.needAutoTranscribe = needAutoTranscribe;
    this.newsChatId = newsChatId;
    this.mediaDownloadService = new MediaDownloadService(new MediaDownloadService.FileSender() {
      @Override
      public void sendText(final Long chatId, final String text) {
        sendTextReply(chatId, text);
      }

      @Override
      public void sendFile(final Long chatId, final byte[] bytes, final String filename,
          final String contentType) {
        // For simplicity, send as document. Telegram sendDocument multipart requires a ByteArrayResource.
        final LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("chat_id", chatId.toString());
        form.add("parse_mode", TELEGRAM_PARSE_MODE);
        form.add("caption", filename);
        form.add("document", new ByteArrayResource(bytes) {
          @Override
          public String getFilename() {
            return filename != null ? filename : "file.bin";
          }

          @Override
          public long contentLength() {
            return bytes.length;
          }
        });

        try {
          telegramClient.post()
              .uri("/bot{token}/sendDocument", botToken)
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(form)
              .retrieve()
              .toBodilessEntity();
        } catch (final RuntimeException ex) {
          log.error("Failed to send downloaded file to chat {}", chatId, ex);
          sendTextReply(chatId, "Failed to upload downloaded file: " + filename);
        }
      }
    }, downloaderEndpoint);
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
    }
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
    final boolean isCommand = text.startsWith("/");

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

    final String normalizedText = StringUtils.hasText(text) ? text.trim() : "";

    log.info("Received Telegram message: author={} {}", message.getFrom().getId(),
        message.getFrom().getUserName());

    if (!hasPhoto && !hasAudioMessage && !hasVideoNote && !hasVideo && !StringUtils.hasText(text)) {
      log.info("Received empty message");
      return;
    }

    if (hasPhoto && (imageBytes == null || imageBytes.length == 0)) {
      log.warn("Failed to download the image.");
      return;
    }
    if (hasAudioMessage && (rawAudioBytes == null || rawAudioBytes.length == 0)) {
      log.warn("Failed to download or convert audio.");
      return;
    }
    
    if ((hasVideoNote || hasVideo) && (extractedAudioFromVideoBytes == null
        || extractedAudioFromVideoBytes.length == 0)) {
      log.warn("Failed to extract audio from video.");
      return;
    }

    if (StringUtils.hasText(normalizedText) && BotUtils.containsMediaUrl(normalizedText)) {
      final String foundUrl = BotUtils.extractFirstUrl(normalizedText);
      if (StringUtils.hasText(foundUrl)) {
        mediaDownloadService.submitDownload(message.getChatId(), foundUrl);
        sendTextReply(message.getChatId(), "Received media link, starting background download...");
        return;
      }
    }

    if ((hasVideoNote || hasVideo) && !isCommand) {
      if (groupChat && needAutoTranscribe) {
        final String transcribed = getTranscribed(true, message, extractedAudioFromVideoBytes,
            aiChatService);
        if (!StringUtils.hasText(transcribed)) {
          log.warn("Failed to transcribe the video audio.");
          return;
        }

        addTranscribedInContext(message.getFrom().getUserName(), message.getFrom().getUserName(),
            "[video] " + transcribed, chatId, chatContext);
      }
      return;
    }

    if (hasAudioMessage && !isCommand) {
      if (groupChat && needAutoTranscribe) {
        final String transcribed = getTranscribed(hasVoice, message, rawAudioBytes, aiChatService);
        if (!StringUtils.hasText(transcribed)) {
          log.warn("Failed to transcribe the audio.");
          return;
        }

        addTranscribedInContext(message.getFrom().getUserName(), message.getFrom().getUserName(),
            "[audio] " + transcribed, chatId, chatContext);
      }
      return;
    }

    if (groupChat) {
      if (!StringUtils.hasText(text)) {
        log.info("Received empty text message in group chat");
        return;
      }

      if (!BotUtils.isAddressedToBot(text, botUsername) && !isCommand) {
        addTranscribedInContext(message.getFrom().getUserName(), message.getFrom().getUserName(),
            text, chatId, chatContext);
        return;
      }
    }

    final ReplyData response = switch (BotUtils.normalizeCommand(normalizedText)) {
      case "/start" -> new ReplyData("The Family bot is started and ready to help you!", false);
      case "/hello" -> new ReplyData("Hello!", false);
      case "/news" -> newsAction(normalizedText, hasPhoto, imageBytes);
      case "/text" -> {
        handleTextCommandFull(message, hasVoice, rawAudioBytes, extractedAudioFromVideoBytes, chatId);
        yield null;
      }
      case "/audio" -> {
        handleAudioCommandFull(message, extractedAudioFromVideoBytes, chatId);
        yield null;
      }
      case "/context" -> {
        final var contextList = chatContext.get(chatId);
        yield new ReplyData(
            contextList != null ? String.join("\n", contextList) : "Context is empty.", false);
      }
      case "/help" -> new ReplyData(BotUtils.HELP, false);
      default -> justMessaging(normalizedText, hasPhoto, chatId, groupChat, imageBytes,
          message.getFrom().getUserName());
    };

    if (response != null) {
      if (response.newsResponse()) {
        sendNewsReply(chatId, response.text(), hasPhoto ? imageBytes : null);
      } else {
        sendReply(chatId, response.text(), hasPhoto ? imageBytes : null);
      }
    }
  }

  private void handleTextCommandFull(final Message message, final boolean hasVoice,
      final byte[] rawAudioBytes, final byte[] extractedAudioFromVideoBytes, final Long chatId) {
    Message targetMessage = message;
    byte[] audioToUse = null;
    boolean isVoice = hasVoice;

    if ((message.hasVideoNote() || message.hasVideo() || message.hasAudio() || message.hasVoice())) {
      targetMessage = message;
      if (message.hasVideoNote() || message.hasVideo()) {
        audioToUse = extractedAudioFromVideoBytes;
      } else if (message.hasVoice() || message.hasAudio()) {
        audioToUse = rawAudioBytes;
        isVoice = message.hasVoice();
      }
    }
    else if (message.getReplyToMessage() != null) {
      final Message replyTo = message.getReplyToMessage();
      if (replyTo.hasVideoNote() || replyTo.hasVideo() || replyTo.hasAudio() || replyTo.hasVoice() || 
          (replyTo.hasDocument() && isMediaDocument(replyTo))) {
        targetMessage = replyTo;
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
      sendTextReply(chatId, "Reply to or send an audio/video message with /text command to transcribe.");
      return;
    }

    try {
      log.info("Transcribing audio...");
      final String transcribed = getTranscribed(isVoice, targetMessage, audioToUse, aiChatService);

      if (!StringUtils.hasText(transcribed)) {
        log.warn("Failed to transcribe the audio.");
        sendTextReply(chatId, "Failed to transcribe the audio. Please try again.");
        return;
      }

      sendTextReply(chatId, transcribed);
    } catch (final Exception ex) {
      log.error("Error processing /text command for chat {}", chatId, ex);
      sendTextReply(chatId, "An error occurred while transcribing: " + ex.getMessage());
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
      sendTextReply(chatId, "Reply to or send a video message with /audio command to extract audio.");
      return;
    }

    try {
      log.info("Sending extracted audio as MP3...");
      sendAudioFile(chatId, audioToSend, "audio.mp3");
    } catch (final Exception ex) {
      log.error("Error processing /audio command for chat {}", chatId, ex);
      sendTextReply(chatId, "An error occurred while extracting audio: " + ex.getMessage());
    }
  }

  private void sendAudioFile(final Long chatId, final byte[] audioBytes, final String filename) {
    final LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("chat_id", chatId.toString());
    form.add("audio", new ByteArrayResource(audioBytes) {
      @Override
      public String getFilename() {
        return filename;
      }

      @Override
      public long contentLength() {
        return audioBytes.length;
      }
    });

    try {
      telegramClient.post()
          .uri("/bot{token}/sendAudio", botToken)
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(form)
          .retrieve()
          .toBodilessEntity();
      log.info("Audio file sent successfully to chat {}", chatId);
    } catch (final RuntimeException ex) {
      log.error("Failed to send audio file to chat {}", chatId, ex);
      sendTextReply(chatId, "Failed to send audio file.");
    }
  }

  private ReplyData justMessaging(final String normalizedText, final boolean hasPhoto,
      final Long chatId, final boolean groupChat, final byte[] imageBytes,
      final String authorUsername) {

    if (StringUtils.hasText(normalizedText) && normalizedText.startsWith("/")) {
      return new ReplyData("Bot does not know this command: " + normalizedText, false);
    }

    final List<String> currentHistory = chatContext.getOrDefault(chatId, Collections.emptyList());
    final String prompt = groupChat
        ? BotUtils.stripBotPrefix(normalizedText, botUsername, currentHistory, groupChat)
        : normalizedText;

    if (groupChat && StringUtils.hasText(normalizedText)) {
      addTranscribedInContext(authorUsername, authorUsername,
          BotUtils.stripTextPrefix(normalizedText), chatId, chatContext);
    }

    if (hasPhoto) {
      final String imagePrompt =
          StringUtils.hasText(normalizedText) ? prompt : "Describe the image in detail.";
      log.info("Image prompt: {}", imagePrompt);
      return chatId == newsChatId && !groupChat
          ? new ReplyData(
          aiChatService.askNewsWithImage(BotUtils.prepareNewsPrompt(imagePrompt), imageBytes,
              "image/jpeg"), true)
          : new ReplyData(aiChatService.askWithImage(imagePrompt, imageBytes, "image/jpeg"), false);
    }

    if (!StringUtils.hasText(prompt)) {
      return null;
    }

    log.debug("Prompt sent to AI: {}", prompt);
    final var result = chatId == newsChatId && !groupChat ?
        new ReplyData(aiChatService.askNews(BotUtils.prepareNewsPrompt(prompt)), true)
        : new ReplyData(aiChatService.ask(prompt), false);

    if (groupChat && result.text() != null) {
      addTranscribedInContext(botUsername, "Bot", result.text(), chatId, chatContext);
    }

    return result;
  }

  private ReplyData newsAction(final String normalizedText, final boolean hasPhoto,
      final byte[] imageBytes) {
    final String promptNews = BotUtils.extractCommandPayload(normalizedText);
    final String preparedPrompt = BotUtils.prepareNewsPrompt(StringUtils.hasText(promptNews)
        ? promptNews
        : "Перепиши новину за інформацією отримавши інформацію з прикріпленого посилання.");

    return hasPhoto
        ? new ReplyData(aiChatService.askNewsWithImage(preparedPrompt, imageBytes, "image/jpeg"),
        true)
        : new ReplyData(aiChatService.askNews(preparedPrompt), true);
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
      if (bestPhoto == null || !StringUtils.hasText(bestPhoto.getFileId())) {
        return null;
      }

      final TelegramFileResponse response = telegramClient.get()
          .uri(uriBuilder -> uriBuilder
              .path("/bot{token}/getFile")
              .queryParam("file_id", bestPhoto.getFileId())
              .build(botToken))
          .retrieve()
          .body(TelegramFileResponse.class);

      if (response == null || response.result() == null || !StringUtils.hasText(
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
    if (!StringUtils.hasText(resolvedFileId)) {
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

      if (response == null || response.result() == null || !StringUtils.hasText(
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
      log.error("Failed to download media from Telegram for chat {}", message.getChatId(), ex);
      return null;
    }
  }


  private void sendReply(final Long chatId, final String text, final byte[] photoBytes) {
    sendReply(chatId, text, photoBytes, null);
  }

  private void sendReply(final Long chatId, final String text, final byte[] photoBytes,
      final String replyMarkupJson) {
    if (photoBytes != null && photoBytes.length > 0) {
      final String caption = BotUtils.buildMarkdownCaption(text);
      if (sendPhotoReply(chatId, photoBytes, caption, replyMarkupJson)) {
        if (!StringUtils.hasText(caption) && StringUtils.hasText(text)) {
          sendTextReply(chatId, text);
        }
        return;
      }
    }
    sendTextReply(chatId, text, replyMarkupJson);
  }

  private boolean sendTextReply(final Long chatId, final String text) {
    return sendTextReply(chatId, text, null);
  }

  private boolean sendTextReply(final Long chatId, final String text,
      final String replyMarkupJson) {
    if (!StringUtils.hasText(text)) {
      return false;
    }

    final LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("chat_id", chatId.toString());
    form.add("text", text);
    form.add("parse_mode", TELEGRAM_PARSE_MODE);
    if (StringUtils.hasText(replyMarkupJson)) {
      form.add("reply_markup", replyMarkupJson);
    }

    try {
      telegramClient.post()
          .uri("/bot{token}/sendMessage", botToken)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form)
          .retrieve()
          .toBodilessEntity();
      return true;
    } catch (final RuntimeException ex) {
      log.error("Failed to send Telegram response to chat {}", chatId, ex);
      return false;
    }
  }

  private boolean sendPhotoReply(final Long chatId, final byte[] photoBytes, final String caption,
      final String replyMarkupJson) {
    final LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("chat_id", chatId.toString());
    form.add("parse_mode", TELEGRAM_PARSE_MODE);
    if (StringUtils.hasText(caption)) {
      form.add("caption", caption);
    }
    if (StringUtils.hasText(replyMarkupJson)) {
      form.add("reply_markup", replyMarkupJson);
    }
    form.add("photo", new ByteArrayResource(photoBytes) {
      @Override
      public String getFilename() {
        return "image.jpg";
      }
    });

    try {
      telegramClient.post()
          .uri("/bot{token}/sendPhoto", botToken)
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(form)
          .retrieve()
          .toBodilessEntity();
      return true;
    } catch (final RuntimeException ex) {
      log.error("Failed to send Telegram photo response to chat {}", chatId, ex);
      return false;
    }
  }

  private void sendNewsReply(final Long chatId, final String text, final byte[] photoBytes) {
    if (!StringUtils.hasText(text)) {
      return;
    }

    final String copyImageToken = photoBytes != null && photoBytes.length > 0
        ? BotUtils.registerCopyImagePayload(text, photoBytes)
        : null;
    final String replyMarkupJson = BotUtils.buildNewsReplyMarkup(
        photoBytes != null && photoBytes.length > 0,
        copyImageToken,
        COPY_IMG_CALLBACK_PREFIX);

    sendReply(chatId, text, photoBytes, replyMarkupJson);
  }

  private void handleCallbackQuery(final CallbackQuery callbackQuery) {
    if (callbackQuery == null || !StringUtils.hasText(callbackQuery.getData())
        || callbackQuery.getMessage() == null) {
      return;
    }

    final String data = callbackQuery.getData();
    if (!data.startsWith(COPY_IMG_CALLBACK_PREFIX)) {
      return;
    }

    final String token = data.substring(COPY_IMG_CALLBACK_PREFIX.length());
    final ReplyPayload payload = BotUtils.getCopyImagePayload(token);
    if (payload == null) {
      answerCallbackQuery(callbackQuery.getId(), "Image is no longer available.");
      return;
    }

    final Long chatId = callbackQuery.getMessage().getChatId();
    final boolean sent = sendCopiedImage(chatId, payload);
    if (sent) {
      BotUtils.removeCopyImagePayload(token);
      answerCallbackQuery(callbackQuery.getId(), null);
    } else {
      answerCallbackQuery(callbackQuery.getId(), "Failed to send image.");
    }
  }

  private boolean sendCopiedImage(final Long chatId, final ReplyPayload payload) {
    if (payload == null || payload.photoBytes() == null || payload.photoBytes().length == 0) {
      return false;
    }
    return sendPhotoReply(chatId, payload.photoBytes(), null, null);
  }

  private void answerCallbackQuery(final String callbackQueryId, final String text) {
    if (!StringUtils.hasText(callbackQueryId)) {
      return;
    }

    final LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("callback_query_id", callbackQueryId);
    if (StringUtils.hasText(text)) {
      form.add("text", text);
    }

    try {
      telegramClient.post()
          .uri("/bot{token}/answerCallbackQuery", botToken)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form)
          .retrieve()
          .toBodilessEntity();
    } catch (final RuntimeException ex) {
      log.warn("Failed to answer Telegram callback query {}", callbackQueryId, ex);
    }
  }

  @PreDestroy
  private void destroy() {
    chatContext.clear();
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