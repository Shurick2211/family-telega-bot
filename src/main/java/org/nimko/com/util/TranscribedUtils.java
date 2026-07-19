package org.nimko.com.util;

import org.nimko.com.ai.AiChatService;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.objects.message.Message;

public class TranscribedUtils {

  private TranscribedUtils() {
  }

  public static String getTranscribed(final boolean hasVoice, final Message message, final byte[] rawAudioBytes, final AiChatService aiChatService) {
//    final String audioMimeType = hasVoice ? "audio/mp3" : resolveAudioMimeType(message);
    return aiChatService.transcribeAudio(rawAudioBytes,  "audio/mp3");
  }

  private static String resolveAudioMimeType(final Message message) {
    if (message == null) {
      return "audio/ogg";
    }
    if (message.hasVoice()) {
      return "audio/ogg";
    }
    if (message.hasAudio() && message.getAudio() != null
        && StringUtils.isNotBlank(message.getAudio().getMimeType())) {
      return message.getAudio().getMimeType();
    }
    if (message.hasVideoNote()) {
      return "audio/mp4";
    }
    return "audio/ogg";
  }

}
