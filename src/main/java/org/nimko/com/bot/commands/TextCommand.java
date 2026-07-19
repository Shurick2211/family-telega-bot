package org.nimko.com.bot.commands;

import static org.nimko.com.util.BotUtils.hasAudioVideo;
import static org.nimko.com.util.TranscribedUtils.getTranscribed;

import com.alibaba.fastjson.JSON;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.bot.BotSenderService;
import org.nimko.com.bot.FamilyTelegramBot.ReplyData;
import org.nimko.com.services.AudioConverter;
import org.nimko.com.services.TelegramFileService;
import org.nimko.com.services.TranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Service
@Order(1)
public class TextCommand implements CommandProcess {

  private static final Logger log = LoggerFactory.getLogger(TextCommand.class);

  private final AiChatService aiChatService;
  private final AudioConverter audioConverter;
  private final BotSenderService botSenderService;
  private final TranslationService translationService;
  private final TelegramFileService telegramFileService;

  public TextCommand(final AiChatService aiChatService,
      final AudioConverter audioConverter,
      final BotSenderService botSenderService,
      final TranslationService translationService,
      final TelegramFileService telegramFileService) {
    this.aiChatService = aiChatService;
    this.audioConverter = audioConverter;
    this.botSenderService = botSenderService;
    this.translationService = translationService;
    this.telegramFileService = telegramFileService;
  }

  @Override
  public boolean isCommand(final String command) {
    return "/text".equals(command);
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final Message message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId, final Map<Long, List<String>> chatContext) {
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
      if (hasAudioVideo(replyTo) || (replyTo.hasDocument() && telegramFileService.isMediaDocument(replyTo))) {
        targetMessage = replyTo;
        final var chatHistory = chatContext.get(chatId);
        if (chatHistory != null) {
          final var replyContextOp = chatHistory.stream()
              .filter(j -> {
                try {
                  return (int) JSON.parseObject(j).getInteger("messageId") == replyTo.getMessageId();
                } catch (final Exception e) {
                  return false;
                }
              }).findFirst();
          if (replyContextOp.isPresent()) {
            final var replyContext = JSON.parseObject(replyContextOp.get());
            final var replyText = replyContext.getString("text");
            if (StringUtils.isNotBlank(replyText)) {
              botSenderService.sendTextReply(chatId, replyText);
              return null;
            }
          }
        }

        final byte[] replyAudioBytes;
        if (replyTo.hasVoice() || replyTo.hasAudio() || (replyTo.hasDocument() && telegramFileService.isMediaDocument(replyTo))) {
          replyAudioBytes = telegramFileService.downloadAudioMessage(replyTo);
          isVoice = replyTo.hasVoice();
        } else {
          replyAudioBytes = telegramFileService.downloadAudioMessage(replyTo);
        }
        
        if (replyAudioBytes != null && replyAudioBytes.length > 0) {
          if (replyTo.hasVoice()) {
            audioToUse = audioConverter.convertOggToMp3(replyAudioBytes);
          } else if (replyTo.hasVideoNote() || replyTo.hasVideo() || (replyTo.hasDocument() && telegramFileService.isMediaDocument(replyTo))) {
            audioToUse = audioConverter.extractAudioFromVideo(replyAudioBytes);
          } else {
            audioToUse = replyAudioBytes;
          }
        }
      }
    }

    if (audioToUse == null || audioToUse.length == 0) {
      botSenderService.sendTextReply(chatId, translationService.getTranslate("bot.text.reply.prompt"));
      return null;
    }

    try {
      log.info("Transcribing audio...");
      final String transcribed = getTranscribed(isVoice, targetMessage, audioToUse, aiChatService);

      if (StringUtils.isBlank(transcribed)) {
        log.warn("Failed to transcribe the audio.");
        botSenderService.sendTextReply(chatId, translationService.getTranslate("bot.text.failed"));
        return null;
      }

      botSenderService.sendTextReply(chatId, transcribed);
    } catch (final Exception ex) {
      log.error("Error processing /text command for chat {}", chatId, ex);
      botSenderService.sendTextReply(chatId, translationService.getTranslate("bot.text.error", ex.getMessage()));
    }
    return null;
  }
}
