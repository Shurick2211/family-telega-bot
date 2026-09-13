package org.nimko.com.bot.messenger;

public interface MediaFileService {

  byte[] downloadBestPhoto(IncomingMessage message);

  byte[] downloadAudioMessage(IncomingMessage message);

  boolean isMediaDocument(IncomingMessage message);

}
