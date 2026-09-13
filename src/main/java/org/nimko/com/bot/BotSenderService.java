package org.nimko.com.bot;

import org.nimko.com.services.MediaDownloadService.FileSender;

public interface BotSenderService extends FileSender {

  void sendReply(Long chatId, String text, byte[] photoBytes);

  void sendReply(Long chatId, String text, byte[] photoBytes, String replyMarkupJson);

  boolean sendTextReply(Long chatId, String text);

  boolean sendTextReply(Long chatId, String text, String replyMarkupJson);

  Integer sendTextAndGetMessageId(Long chatId, String text);

  void editMessageText(Long chatId, Integer messageId, String text);

  boolean sendPhotoReply(Long chatId, byte[] photoBytes, String caption, String replyMarkupJson);

  void sendNewsReply(Long chatId, String text, byte[] photoBytes);

  void sendAudioFile(Long chatId, byte[] audioBytes, String filename);

  void sendDocument(Long chatId, byte[] bytes, String filename);

  void answerCallbackQuery(String callbackQueryId, String text);

}
