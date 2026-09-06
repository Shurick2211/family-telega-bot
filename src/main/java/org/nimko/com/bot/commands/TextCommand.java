package org.nimko.com.bot.commands;

import static org.nimko.com.util.TranscribedUtils.getTranscribed;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.bot.BotSenderService;
import org.nimko.com.bot.dto.ReplyData;
import org.nimko.com.bot.messenger.MediaFileService;
import org.nimko.com.repository.ChatContextRepository;
import org.nimko.com.services.AudioConverter;
import org.nimko.com.services.I18nService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.nimko.com.bot.messenger.IncomingMessage;

@Service
@Order(1)
@RequiredArgsConstructor
public class TextCommand implements CommandProcess {

  private static final Logger log = LoggerFactory.getLogger(TextCommand.class);

  private final AiChatService aiChatService;
  private final AudioConverter audioConverter;
  private final BotSenderService botSenderService;
  private final I18nService i18nService;
  private final MediaFileService telegramFileService;
  private final ChatContextRepository chatContextRepository;

  @Override
  public boolean isCommand(final String command) {
    return "/text".equals(command);
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto,
      final byte[] imageBytes,
      final IncomingMessage message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId) {
    IncomingMessage targetMessage = message;
    byte[] audioToUse = null;
    boolean isVoice = hasVoice;

    if (message.hasAudioVideo()) {
      if (message.hasVideoNote() || message.hasVideo()) {
        audioToUse = extractedAudioFromVideoBytes;
      } else if (message.hasVoice() || message.hasAudio()) {
        audioToUse = rawAudioBytes;
        isVoice = message.hasVoice();
      }
    } else if (message.getReplyToMessage() != null) {
      final IncomingMessage replyTo = message.getReplyToMessage();
      if (replyTo.hasAudioVideo() || (replyTo.hasDocument() && telegramFileService.isMediaDocument(
          replyTo))) {
        targetMessage = replyTo;
        final var replyContextOp = chatContextRepository.findByChatIdAndMessageId(chatId,
            targetMessage.getMessageId());
        if (replyContextOp.isPresent()) {
          final var replyText = replyContextOp.get().getMessage();
          if (StringUtils.isNotBlank(replyText)) {
            botSenderService.sendTextReply(chatId, replyText);
            return null;
          }
        }
      }

      final byte[] replyAudioBytes;
      if (replyTo.hasVoice() || replyTo.hasAudio() || (replyTo.hasDocument()
          && telegramFileService.isMediaDocument(replyTo))) {
        replyAudioBytes = telegramFileService.downloadAudioMessage(replyTo);
        isVoice = replyTo.hasVoice();
      } else {
        replyAudioBytes = telegramFileService.downloadAudioMessage(replyTo);
      }

      if (replyAudioBytes != null && replyAudioBytes.length > 0) {
        if (replyTo.hasVoice()) {
          audioToUse = audioConverter.convertOggToMp3(replyAudioBytes);
        } else if (replyTo.hasVideoNote() || replyTo.hasVideo() || (replyTo.hasDocument()
            && telegramFileService.isMediaDocument(replyTo))) {
          audioToUse = audioConverter.extractAudioFromVideo(replyAudioBytes);
        } else {
          audioToUse = replyAudioBytes;
        }
      }
    }

    if (audioToUse == null || audioToUse.length == 0) {
      botSenderService.sendTextReply(chatId,
          i18nService.getTranslate("bot.text.reply.prompt"));
      return null;
    }

    try {
      log.info("Transcribing audio...");
      final String transcribed = getTranscribed(isVoice, targetMessage, audioToUse, aiChatService);

      if (StringUtils.isBlank(transcribed)) {
        log.warn("Failed to transcribe the audio.");
        botSenderService.sendTextReply(chatId, i18nService.getTranslate("bot.text.failed"));
        return null;
      }

      botSenderService.sendTextReply(chatId, transcribed);
    } catch (final Exception ex) {
      log.error("Error processing /text command for chat {}", chatId, ex);
      botSenderService.sendTextReply(chatId,
          i18nService.getTranslate("bot.text.error", ex.getMessage()));
    }
    return null;
  }
}
