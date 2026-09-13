package org.nimko.com.bot.messenger;

import org.apache.commons.lang3.StringUtils;

public interface IncomingMessage {

  Long getChatId();

  Integer getMessageId();

  String getText();

  boolean hasPhoto();

  boolean hasVoice();

  boolean hasAudio();

  boolean hasVideoNote();

  boolean hasVideo();

  boolean hasDocument();

  String getDocumentFileName();

  boolean isGroupChat();

  MessengerUser getFrom();

  IncomingMessage getReplyToMessage();

  boolean isReplyToBot(String botUsername);

  default boolean hasAudioVideo() {
    return hasVideoNote() || hasVideo() || hasAudio() || hasVoice();
  }

  default boolean hasUserContent() {
    return StringUtils.isNotBlank(getText()) || hasPhoto() || hasVoice() || hasAudio()
        || hasVideoNote() || hasDocument();
  }

}
