package org.nimko.com.impl_bot.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import org.apache.commons.lang3.StringUtils;
import org.nimko.com.bot.messenger.IncomingMessage;
import org.nimko.com.bot.messenger.MediaFileService;
import org.nimko.com.impl_bot.telegram.messanger.TelegramIncomingMessage;
import org.nimko.com.config.TelegramBotProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Service
public class TelegramFileService implements MediaFileService {

  private static final Logger log = LoggerFactory.getLogger(TelegramFileService.class);

  private final String botToken;
  private final String telegramApiBaseUrl;
  private final RestClient telegramClient;

  public TelegramFileService(final TelegramBotProperties telegramProperties) {
    this.botToken = telegramProperties.token();
    this.telegramApiBaseUrl = StringUtils.isNotBlank(telegramProperties.apiBaseUrl())
        ? telegramProperties.apiBaseUrl()
        : "https://api.telegram.org";
    this.telegramClient = RestClient.builder()
        .baseUrl(telegramApiBaseUrl)
        .build();
  }

  public byte[] downloadBestPhoto(final Message message) {
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

      final String fileUrl = TelegramBotUtils.buildTelegramFileUrl(telegramApiBaseUrl, botToken,
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

  public byte[] downloadAudioMessage(final Message message) {
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

      final String fileUrl = TelegramBotUtils.buildTelegramFileUrl(telegramApiBaseUrl, botToken,
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

  public boolean isMediaDocument(final Message message) {
    if (!message.hasDocument() || message.getDocument() == null) {
      return false;
    }
    final String mimeType = message.getDocument().getMimeType();
    return mimeType != null && (mimeType.startsWith("audio/") || mimeType.startsWith("video/"));
  }

  @Override
  public byte[] downloadBestPhoto(final IncomingMessage message) {
    return downloadBestPhoto(unwrap(message));
  }

  @Override
  public byte[] downloadAudioMessage(final IncomingMessage message) {
    return downloadAudioMessage(unwrap(message));
  }

  @Override
  public boolean isMediaDocument(final IncomingMessage message) {
    return isMediaDocument(unwrap(message));
  }

  private static Message unwrap(final IncomingMessage message) {
    if (message instanceof final TelegramIncomingMessage telegramMessage) {
      return telegramMessage.raw();
    }
    throw new IllegalArgumentException("Unsupported message type: " + message.getClass());
  }

  private record TelegramFileResponse(boolean ok, TelegramFile result) {}

  private record TelegramFile(@JsonProperty("file_path") String filePath) {}
}
