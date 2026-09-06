package org.nimko.com.util;

import org.nimko.com.ai.AiChatService;
import org.nimko.com.bot.messenger.IncomingMessage;

public class TranscribedUtils {

  private TranscribedUtils() {
  }

  public static String getTranscribed(final boolean hasVoice, final IncomingMessage message, final byte[] rawAudioBytes, final AiChatService aiChatService) {
    return aiChatService.transcribeAudio(rawAudioBytes,  "audio/mp3");
  }

}
