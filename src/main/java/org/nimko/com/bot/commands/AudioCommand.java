package org.nimko.com.bot.commands;

import java.util.List;
import java.util.Map;
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
public class AudioCommand implements CommandProcess {

  private static final Logger log = LoggerFactory.getLogger(AudioCommand.class);

  private final AudioConverter audioConverter;
  private final BotSenderService botSenderService;
  private final TranslationService translationService;
  private final TelegramFileService telegramFileService;

  public AudioCommand(final AudioConverter audioConverter,
      final BotSenderService botSenderService,
      final TranslationService translationService,
      final TelegramFileService telegramFileService) {
    this.audioConverter = audioConverter;
    this.botSenderService = botSenderService;
    this.translationService = translationService;
    this.telegramFileService = telegramFileService;
  }

  @Override
  public boolean isCommand(final String command) {
    return "/audio".equals(command);
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final Message message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId, final Map<Long, List<String>> chatContext) {
    byte[] audioToSend = null;

    if (message.hasVideoNote() || message.hasVideo()) {
      audioToSend = extractedAudioFromVideoBytes;
    }
    else if (message.getReplyToMessage() != null && (message.getReplyToMessage().hasVideoNote() || message.getReplyToMessage().hasVideo() || 
             (message.getReplyToMessage().hasDocument() && telegramFileService.isMediaDocument(message.getReplyToMessage())))) {
      final Message replyTo = message.getReplyToMessage();
      final byte[] replyVideoBytes = telegramFileService.downloadAudioMessage(replyTo);
      if (replyVideoBytes != null && replyVideoBytes.length > 0) {
        audioToSend = audioConverter.extractAudioFromVideo(replyVideoBytes);
      }
    }

    if (audioToSend == null || audioToSend.length == 0) {
      botSenderService.sendTextReply(chatId, translationService.getTranslate("bot.audio.reply.prompt"));
      return null;
    }

    try {
      log.info("Sending extracted audio as MP3...");
      botSenderService.sendAudioFile(chatId, audioToSend, "audio.mp3");
    } catch (final Exception ex) {
      log.error("Error processing /audio command for chat {}", chatId, ex);
      botSenderService.sendTextReply(chatId, translationService.getTranslate("bot.audio.error", ex.getMessage()));
    }
    return null;
  }
}
