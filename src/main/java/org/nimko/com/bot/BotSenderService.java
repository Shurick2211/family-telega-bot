package org.nimko.com.bot;

import org.apache.commons.lang3.StringUtils;
import org.nimko.com.config.TelegramBotProperties;
import org.nimko.com.services.MediaDownloadService.FileSender;
import org.nimko.com.services.TranslationService;
import org.nimko.com.util.BotUtils;
import org.nimko.com.util.BotUtils.ReplyPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class BotSenderService implements FileSender {

  private static final Logger log = LoggerFactory.getLogger(BotSenderService.class);
  public static final String COPY_IMG_CALLBACK_PREFIX = "copy_img:";
  private static final String TELEGRAM_PARSE_MODE = "Markdown";

  private final String botToken;
  private final RestClient telegramClient;
  private final TranslationService translationService;

  public BotSenderService(
      final TelegramBotProperties telegramProperties,
      final TranslationService translationService) {
    this.botToken = telegramProperties.token();
    final String apiBaseUrl = StringUtils.isNotBlank(telegramProperties.apiBaseUrl())
        ? telegramProperties.apiBaseUrl()
        : "https://api.telegram.org";
    this.telegramClient = RestClient.builder()
        .baseUrl(apiBaseUrl)
        .build();
    this.translationService = translationService;
  }

  @Override
  public void sendText(final Long chatId, final String text) {
    sendTextReply(chatId, text);
  }

  @Override
  public void sendFile(final Long chatId, final byte[] bytes, final String filename,
      final String contentType) {
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
      sendTextReply(chatId, translationService.getTranslate("bot.media.upload.failed", filename));
    }
  }

  public void sendReply(final Long chatId, final String text, final byte[] photoBytes) {
    sendReply(chatId, text, photoBytes, null);
  }

  public void sendReply(final Long chatId, final String text, final byte[] photoBytes,
      final String replyMarkupJson) {
    if (photoBytes != null && photoBytes.length > 0) {
      final String caption = BotUtils.buildMarkdownCaption(text);
      if (sendPhotoReply(chatId, photoBytes, caption, replyMarkupJson)) {
        if (StringUtils.isBlank(caption) && StringUtils.isNotBlank(text)) {
          sendTextReply(chatId, text);
        }
        return;
      }
    }
    sendTextReply(chatId, text, replyMarkupJson);
  }

  public boolean sendTextReply(final Long chatId, final String text) {
    return sendTextReply(chatId, text, null);
  }

  public boolean sendTextReply(final Long chatId, final String text,
      final String replyMarkupJson) {
    if (StringUtils.isBlank(text)) {
      return false;
    }

    final LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("chat_id", chatId.toString());
    form.add("text", text);
    form.add("parse_mode", TELEGRAM_PARSE_MODE);
    if (StringUtils.isNotBlank(replyMarkupJson)) {
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

  public boolean sendPhotoReply(final Long chatId, final byte[] photoBytes, final String caption,
      final String replyMarkupJson) {
    final LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("chat_id", chatId.toString());
    form.add("parse_mode", TELEGRAM_PARSE_MODE);
    if (StringUtils.isNotBlank(caption)) {
      form.add("caption", caption);
    }
    if (StringUtils.isNotBlank(replyMarkupJson)) {
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

  public void sendNewsReply(final Long chatId, final String text, final byte[] photoBytes) {
    if (StringUtils.isBlank(text)) {
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

  public void sendAudioFile(final Long chatId, final byte[] audioBytes, final String filename) {
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
      sendTextReply(chatId, translationService.getTranslate("bot.audio.failed"));
    }
  }

  public void sendDocument(final Long chatId, final byte[] bytes, final String filename) {
    final LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("chat_id", chatId.toString());
    form.add("document", new ByteArrayResource(bytes) {
      @Override
      public String getFilename() {
        return filename;
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
      log.info("Document sent successfully to chat {}", chatId);
    } catch (final RuntimeException ex) {
      log.error("Failed to send document to chat {}", chatId, ex);
      sendTextReply(chatId, "Failed to send generated document.");
    }
  }

  public void answerCallbackQuery(final String callbackQueryId, final String text) {
    if (StringUtils.isBlank(callbackQueryId)) {
      return;
    }

    final LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("callback_query_id", callbackQueryId);
    if (StringUtils.isNotBlank(text)) {
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

  public boolean sendCopiedImage(final Long chatId, final ReplyPayload payload) {
    if (payload == null || payload.photoBytes() == null || payload.photoBytes().length == 0) {
      return false;
    }
    return sendPhotoReply(chatId, payload.photoBytes(), null, null);
  }
}